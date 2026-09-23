package com.gdufs.schedule.domain.schedule

import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.TimeSlot
import java.time.LocalTime

/**
 * 今日页的"正在进行 / 下一节"判定(纯 Kotlin,便于单元测试)。
 * 输入来自 [ScheduleQuery.coursesOnDay] 按起始大节升序的结果,
 * 时间基准为作息方案中的 [TimeSlot.startTime]/[TimeSlot.endTime]("HH:mm")。
 */
object TodaySchedule {

    /** "HH:mm" 文本 → 自零点起的分钟数;格式非法时返回 null */
    fun parseMinutes(text: String): Int? {
        val parts = text.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    /** 上课安排对应的实际起止分钟数(跨大节时取起始大节的开始与结束大节的结束) */
    private fun slotBounds(
        occurrence: CourseOccurrence,
        slots: List<TimeSlot>,
    ): Pair<Int, Int>? {
        val start = slots.firstOrNull { it.slotNumber == occurrence.startSlot } ?: return null
        val end = slots.firstOrNull { it.slotNumber == occurrence.endSlot } ?: return null
        val startMinutes = parseMinutes(start.startTime) ?: return null
        val endMinutes = parseMinutes(end.endTime) ?: return null
        return startMinutes to endMinutes
    }

    /**
     * 正在进行的课程:实际时间区间 [开始, 结束) 包含 [now] 的第一条安排;
     * 时间段缺失或时间解析失败时视为不匹配。
     */
    fun ongoing(
        entries: List<DayCourseEntry>,
        slots: List<TimeSlot>,
        now: LocalTime,
    ): DayCourseEntry? {
        val nowMinutes = now.hour * 60 + now.minute
        return entries.firstOrNull { entry ->
            val bounds = slotBounds(entry.occurrence, slots) ?: return@firstOrNull false
            nowMinutes >= bounds.first && nowMinutes < bounds.second
        }
    }

    /**
     * 下一节课程:开始时间严格晚于 [now] 的第一条安排。
     * [entries] 须已按起始大节升序(即 [ScheduleQuery.coursesOnDay] 的返回顺序)。
     */
    fun next(
        entries: List<DayCourseEntry>,
        slots: List<TimeSlot>,
        now: LocalTime,
    ): DayCourseEntry? {
        val nowMinutes = now.hour * 60 + now.minute
        return entries.firstOrNull { entry ->
            val start = slots.firstOrNull { it.slotNumber == entry.occurrence.startSlot }
                ?: return@firstOrNull false
            val startMinutes = parseMinutes(start.startTime) ?: return@firstOrNull false
            startMinutes > nowMinutes
        }
    }

    /** 安排的实际开始时间文本("HH:mm");无法解析时返回 null */
    fun startTimeOf(
        entry: DayCourseEntry,
        slots: List<TimeSlot>,
    ): String? = slots.firstOrNull { it.slotNumber == entry.occurrence.startSlot }?.startTime
}