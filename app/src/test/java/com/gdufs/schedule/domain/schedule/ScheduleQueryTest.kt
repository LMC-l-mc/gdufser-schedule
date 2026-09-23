package com.gdufs.schedule.domain.schedule

import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.CourseWithOccurrences
import com.gdufs.schedule.data.model.WeekMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 按天/按周查询有效课程与课程冲突检测的边界测试。
 * 学期:2026-02-23(周一)开学,共 16 周,UTC 时区。
 * 第 2 周周一为 2026-03-02,第 3 周周一为 2026-03-09。
 */
class ScheduleQueryTest {

    private val zone = ZoneOffset.UTC
    private val termStart = ms(2026, 2, 23)
    private val week2Monday = ms(2026, 3, 2)
    private val week3Monday = ms(2026, 3, 9)

    private fun ms(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun course(name: String, enabled: Boolean = true) =
        Course(courseTableId = 1L, name = name, isEnabled = enabled)

    private fun occurrence(
        weekday: Int,
        start: Int,
        end: Int = start,
        mode: WeekMode = WeekMode.EVERY_WEEK,
        custom: String = "",
    ) = CourseOccurrence(
        courseId = 0L,
        weekday = weekday,
        startSlot = start,
        endSlot = end,
        weekMode = mode,
        customWeeksJson = custom,
    )

    private fun withOccurrences(course: Course, occurrences: List<CourseOccurrence>) =
        CourseWithOccurrences(course, occurrences)

    // ---------- 按天查询 ----------

    @Test
    fun `查询某一天有效课程_按周次规则过滤并排序`() {
        val courses = listOf(
            withOccurrences(course("甲"), listOf(occurrence(weekday = 1, start = 1, end = 2))),
            withOccurrences(course("乙"), listOf(occurrence(weekday = 2, start = 3))),
            withOccurrences(course("丙"), listOf(occurrence(weekday = 1, start = 4, mode = WeekMode.ODD_WEEKS))),
            withOccurrences(course("丁"), listOf(occurrence(weekday = 1, start = 5, mode = WeekMode.CUSTOM, custom = "[2]"))),
            withOccurrences(course("停用课", enabled = false), listOf(occurrence(weekday = 1, start = 6))),
        )

        // 第 2 周(偶数周)周一:甲 + 丁,丙(单周)与乙(周二)与停用课不出现
        val day2 = ScheduleQuery.coursesOnDay(courses, week2Monday, termStart, 1, 16, zone)
        assertEquals(listOf("甲" to 1, "丁" to 5), day2.map { it.course.name to it.occurrence.startSlot })

        // 第 3 周(奇数周)周一:甲 + 丙,丁(仅第 2 周)不出现
        val day3 = ScheduleQuery.coursesOnDay(courses, week3Monday, termStart, 1, 16, zone)
        assertEquals(listOf("甲" to 1, "丙" to 4), day3.map { it.course.name to it.occurrence.startSlot })
    }

    @Test
    fun `查询学期外日期返回空列表`() {
        val courses = listOf(
            withOccurrences(course("甲"), listOf(occurrence(weekday = 1, start = 1))),
        )
        // 开学前:2026-02-16(第 1 周首日之前)
        assertEquals(
            emptyList<DayCourseEntry>(),
            ScheduleQuery.coursesOnDay(courses, ms(2026, 2, 16), termStart, 1, 16, zone),
        )
        // 学期结束后:2026-06-22(第 16 周之后)
        assertEquals(
            emptyList<DayCourseEntry>(),
            ScheduleQuery.coursesOnDay(courses, ms(2026, 6, 22), termStart, 1, 16, zone),
        )
    }

    @Test
    fun `同一天多条上课安排的课程出现多次`() {
        val courses = listOf(
            withOccurrences(
                course("甲"),
                listOf(occurrence(weekday = 1, start = 1), occurrence(weekday = 1, start = 6)),
            ),
            withOccurrences(course("乙"), listOf(occurrence(weekday = 1, start = 1))),
        )
        val day = ScheduleQuery.coursesOnDay(courses, week2Monday, termStart, 1, 16, zone)
        assertEquals(listOf("甲" to 1, "乙" to 1, "甲" to 6), day.map { it.course.name to it.occurrence.startSlot })
    }

    // ---------- 按周查询 ----------

    @Test
    fun `查询某一周有效课程_按星期与大节排序且含具体日期`() {
        val courses = listOf(
            withOccurrences(course("甲"), listOf(occurrence(weekday = 2, start = 3, end = 4))),
            withOccurrences(course("乙"), listOf(occurrence(weekday = 1, start = 2))),
            withOccurrences(course("丙"), listOf(occurrence(weekday = 1, start = 1, mode = WeekMode.CUSTOM, custom = "[2,3]"))),
            withOccurrences(course("停用课", enabled = false), listOf(occurrence(weekday = 1, start = 1))),
        )

        val week = ScheduleQuery.coursesInWeek(courses, week = 2, termStart, 1, zone)
        assertEquals(
            listOf(
                Triple("丙", 1, ms(2026, 3, 2)), // 第 2 周周一,自定义周次 [2,3] 命中
                Triple("乙", 1, ms(2026, 3, 2)),
                Triple("甲", 2, ms(2026, 3, 3)), // 第 2 周周二
            ),
            week.map { Triple(it.course.name, it.weekday, it.dateEpochMillis) },
        )
    }

    @Test
    fun `跨多个大节的课程在周查询中保留大节区间`() {
        val courses = listOf(
            withOccurrences(course("甲"), listOf(occurrence(weekday = 4, start = 3, end = 5))),
        )
        val week = ScheduleQuery.coursesInWeek(courses, week = 1, termStart, 1, zone)
        assertEquals(1, week.size)
        assertEquals(3, week.first().occurrence.startSlot)
        assertEquals(5, week.first().occurrence.endSlot)
        assertEquals(4, week.first().weekday)
        assertEquals(ms(2026, 2, 26), week.first().dateEpochMillis) // 第 1 周周四
    }

    // ---------- 冲突检测:大节区间 ----------

    @Test
    fun `同一星期大节重叠才冲突_含端点相接`() {
        val early = occurrence(weekday = 1, start = 1, end = 2)
        val later = occurrence(weekday = 1, start = 2, end = 3)
        val after = occurrence(weekday = 1, start = 3, end = 4)
        val otherDay = occurrence(weekday = 2, start = 1, end = 2)

        assertTrue("共享第 2 大节应重叠", ScheduleQuery.slotsOverlap(early, later))
        assertFalse("相邻不重叠", ScheduleQuery.slotsOverlap(early, after))
        assertFalse("不同星期不重叠", ScheduleQuery.slotsOverlap(early, otherDay))
    }

    @Test
    fun `第一大节与第二大节相邻不冲突_第七大节同位冲突`() {
        val slot1 = occurrence(weekday = 1, start = 1)
        val slot2 = occurrence(weekday = 1, start = 2)
        val slot7Friday = occurrence(weekday = 5, start = 7)
        val slot7Monday = occurrence(weekday = 1, start = 7)

        assertFalse("第 1 大节与第 2 大节相邻不重叠", ScheduleQuery.slotsOverlap(slot1, slot2))
        assertFalse("星期不同则第 7 大节也不重叠", ScheduleQuery.slotsOverlap(slot7Friday, slot7Monday))
        assertTrue("同一星期第 7 大节重叠", ScheduleQuery.slotsOverlap(slot7Monday, occurrence(weekday = 1, start = 7)))
    }

    @Test
    fun `跨多个大节课程的冲突检测`() {
        val span3to5 = occurrence(weekday = 3, start = 3, end = 5)
        assertTrue("3-5 与 5-6 共享第 5 节", ScheduleQuery.slotsOverlap(span3to5, occurrence(weekday = 3, start = 5, end = 6)))
        assertFalse("3-5 与 1-2 不重叠", ScheduleQuery.slotsOverlap(span3to5, occurrence(weekday = 3, start = 1, end = 2)))
        assertTrue("1-7 包含 2-6", ScheduleQuery.slotsOverlap(occurrence(weekday = 3, start = 1, end = 7), occurrence(weekday = 3, start = 2, end = 6)))
        assertTrue("1-7 覆盖任意同星期大节", ScheduleQuery.slotsOverlap(occurrence(weekday = 3, start = 1, end = 7), occurrence(weekday = 3, start = 7)))
    }

    // ---------- 冲突检测:周次规则 ----------

    @Test
    fun `单周与双周同位不冲突_单周与每周冲突`() {
        val odd = occurrence(weekday = 1, start = 1, mode = WeekMode.ODD_WEEKS)
        val even = occurrence(weekday = 1, start = 1, mode = WeekMode.EVEN_WEEKS)
        val every = occurrence(weekday = 1, start = 1, mode = WeekMode.EVERY_WEEK)

        assertFalse("单双周无交集周次", ScheduleQuery.conflictsInAnyWeek(odd, even, 16))
        assertTrue("单周与每周有交集", ScheduleQuery.conflictsInAnyWeek(odd, every, 16))
        assertTrue("双周与每周有交集", ScheduleQuery.conflictsInAnyWeek(even, every, 16))
    }

    @Test
    fun `自定义周次与其他规则按交集判断冲突`() {
        val custom24 = occurrence(weekday = 1, start = 1, mode = WeekMode.CUSTOM, custom = "[2,4]")
        val custom35 = occurrence(weekday = 1, start = 1, mode = WeekMode.CUSTOM, custom = "[3,5]")
        val custom4 = occurrence(weekday = 1, start = 1, mode = WeekMode.CUSTOM, custom = "[4]")
        val even = occurrence(weekday = 1, start = 1, mode = WeekMode.EVEN_WEEKS)
        val odd = occurrence(weekday = 1, start = 1, mode = WeekMode.ODD_WEEKS)
        val every = occurrence(weekday = 1, start = 1, mode = WeekMode.EVERY_WEEK)

        assertFalse("[2,4] 与 [3,5] 无交集", ScheduleQuery.conflictsInAnyWeek(custom24, custom35, 16))
        assertTrue("[4] 与双周在第 4 周交集", ScheduleQuery.conflictsInAnyWeek(custom4, even, 16))
        assertTrue("[2,4] 与每周有交集", ScheduleQuery.conflictsInAnyWeek(custom24, every, 16))
        assertFalse("[2,4] 与单周无交集", ScheduleQuery.conflictsInAnyWeek(custom24, odd, 16))
    }

    // ---------- findConflicts ----------

    @Test
    fun `查找课程列表中的冲突对`() {
        val courses = listOf(
            withOccurrences(course("甲"), listOf(occurrence(weekday = 1, start = 1, end = 2))),
            withOccurrences(course("乙"), listOf(occurrence(weekday = 1, start = 2, end = 3))),
            withOccurrences(course("丙"), listOf(occurrence(weekday = 2, start = 1, end = 2))),
        )
        val conflicts = ScheduleQuery.findConflicts(courses, 16)
        assertEquals(1, conflicts.size)
        assertEquals("甲", conflicts.first().firstCourse.name)
        assertEquals("乙", conflicts.first().secondCourse.name)
    }

    @Test
    fun `同一课程内部多条安排冲突也会被报告`() {
        val courses = listOf(
            withOccurrences(
                course("甲"),
                listOf(occurrence(weekday = 1, start = 5, end = 6), occurrence(weekday = 1, start = 6, end = 7)),
            ),
        )
        val conflicts = ScheduleQuery.findConflicts(courses, 16)
        assertEquals(1, conflicts.size)
        assertEquals("甲", conflicts.first().firstCourse.name)
        assertEquals("甲", conflicts.first().secondCourse.name)
        assertEquals(5 to 6, conflicts.first().firstOccurrence.startSlot to conflicts.first().firstOccurrence.endSlot)
    }

    @Test
    fun `停用课程不参与冲突检测`() {
        val courses = listOf(
            withOccurrences(course("甲"), listOf(occurrence(weekday = 1, start = 1))),
            withOccurrences(course("停用课", enabled = false), listOf(occurrence(weekday = 1, start = 1))),
        )
        assertTrue(ScheduleQuery.findConflicts(courses, 16).isEmpty())
    }
}