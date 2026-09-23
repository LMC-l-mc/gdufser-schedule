package com.gdufs.schedule.domain.schedule

import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.CourseWithOccurrences
import java.time.ZoneId

/** 某一天有效的课程条目:课程 + 当天生效的一条上课安排 */
data class DayCourseEntry(
    val course: Course,
    val occurrence: CourseOccurrence,
)

/** 某一周有效的课程条目:课程 + 上课安排 + 具体日期与星期 */
data class WeekCourseEntry(
    val dateEpochMillis: Long,
    val weekday: Int,
    val course: Course,
    val occurrence: CourseOccurrence,
)

/** 课程冲突:同一星期、大节区间重叠、且在第 1..totalWeeks 周内至少有一周同时生效 */
data class CourseConflict(
    val firstCourse: Course,
    val firstOccurrence: CourseOccurrence,
    val secondCourse: Course,
    val secondOccurrence: CourseOccurrence,
)

/**
 * 基于内存课程列表的查询与冲突检测(纯 Kotlin,不做数据库访问)。
 * 数据由 Repository 从数据库读出后,将列表传入本工具计算。
 * 停用的课程(isEnabled=false)不参与课表展示,故统一被排除。
 */
object ScheduleQuery {

    /**
     * 查询指定日期有效的课程条目,按起始大节升序。
     * 日期不在学期内(未开始或已结束)时返回空列表。
     */
    fun coursesOnDay(
        courses: List<CourseWithOccurrences>,
        dateEpochMillis: Long,
        termStartEpochMillis: Long,
        firstDayOfWeek: Int,
        totalWeeks: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DayCourseEntry> {
        val week = (
            ScheduleCalculator.weekOfTerm(
                dateEpochMillis,
                termStartEpochMillis,
                firstDayOfWeek,
                totalWeeks,
                zone,
            ) as? TermWeek.InTerm
            )?.week ?: return emptyList()
        val weekday = ScheduleCalculator.weekdayOf(dateEpochMillis, zone)
        return courses.asSequence()
            .filter { it.course.isEnabled }
            .flatMap { item ->
                item.occurrences.asSequence()
                    .filter { it.weekday == weekday && it.isActiveInWeek(week) }
                    .map { DayCourseEntry(item.course, it) }
            }
            .sortedBy { it.occurrence.startSlot }
            .toList()
    }

    /**
     * 查询第 [week] 周(1 起)七天有效的课程条目,按星期与大节升序。
     */
    fun coursesInWeek(
        courses: List<CourseWithOccurrences>,
        week: Int,
        termStartEpochMillis: Long,
        firstDayOfWeek: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<WeekCourseEntry> {
        require(week >= 1) { "week 必须 >= 1,实际为 $week" }
        return courses.asSequence()
            .filter { it.course.isEnabled }
            .flatMap { item ->
                item.occurrences.asSequence()
                    .filter { it.isActiveInWeek(week) }
                    .map { occurrence ->
                        WeekCourseEntry(
                            dateEpochMillis = ScheduleCalculator.dateOfWeek(
                                week,
                                occurrence.weekday,
                                termStartEpochMillis,
                                firstDayOfWeek,
                                zone,
                            ),
                            weekday = occurrence.weekday,
                            course = item.course,
                            occurrence = occurrence,
                        )
                    }
            }
            .sortedWith(compareBy({ it.weekday }, { it.occurrence.startSlot }))
            .toList()
    }

    /**
     * 两个上课安排是否同星期且大节区间重叠,支持跨多个大节(startSlot..endSlot 含端点)。
     * 共享同一大节即视为重叠,例如第 1-2 节与第 2-3 节重叠(共享第 2 大节)。
     */
    fun slotsOverlap(a: CourseOccurrence, b: CourseOccurrence): Boolean =
        a.weekday == b.weekday && a.startSlot <= b.endSlot && b.startSlot <= a.endSlot

    /**
     * 两个上课安排是否构成冲突:同星期、大节区间重叠,
     * 且周次规则在第 1..totalWeeks 周内存在交集(例如单周与双周同位不冲突)。
     */
    fun conflictsInAnyWeek(a: CourseOccurrence, b: CourseOccurrence, totalWeeks: Int): Boolean {
        if (!slotsOverlap(a, b)) return false
        return (1..totalWeeks).any { a.isActiveInWeek(it) && b.isActiveInWeek(it) }
    }

    /**
     * 查找启用课程列表中的全部冲突对(含同一课程内部多条安排的冲突),每对只报告一次。
     */
    fun findConflicts(
        courses: List<CourseWithOccurrences>,
        totalWeeks: Int,
    ): List<CourseConflict> {
        val enabled = courses.filter { it.course.isEnabled }
        val conflicts = mutableListOf<CourseConflict>()

        fun record(
            first: CourseWithOccurrences,
            firstOccurrence: CourseOccurrence,
            second: CourseWithOccurrences,
            secondOccurrence: CourseOccurrence,
        ) {
            if (conflictsInAnyWeek(firstOccurrence, secondOccurrence, totalWeeks)) {
                conflicts += CourseConflict(
                    firstCourse = first.course,
                    firstOccurrence = firstOccurrence,
                    secondCourse = second.course,
                    secondOccurrence = secondOccurrence,
                )
            }
        }

        enabled.forEachIndexed { i, first ->
            // 同一课程内部的冲突
            first.occurrences.forEachIndexed { x, a ->
                for (y in x + 1 until first.occurrences.size) {
                    record(first, a, first, first.occurrences[y])
                }
            }
            // 与其他课程的冲突
            for (j in i + 1 until enabled.size) {
                val second = enabled[j]
                first.occurrences.forEach { a ->
                    second.occurrences.forEach { b -> record(first, a, second, b) }
                }
            }
        }
        return conflicts
    }

    /**
     * 检查一条候选课程(新增或编辑后的课程及其安排)与现有课程的冲突,
     * 用于保存前的冲突提示。返回涉及该候选课程的全部冲突对;
     * 候选课程自身的内部冲突也会被包含。
     */
    fun conflictsWith(
        candidate: CourseWithOccurrences,
        existing: List<CourseWithOccurrences>,
        totalWeeks: Int,
    ): List<CourseConflict> {
        val all = findConflicts(listOf(candidate) + existing, totalWeeks)
        return all.filter { conflict ->
            conflict.firstCourse == candidate.course || conflict.secondCourse == candidate.course
        }
    }
}