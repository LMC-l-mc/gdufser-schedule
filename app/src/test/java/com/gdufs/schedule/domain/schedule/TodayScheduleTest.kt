package com.gdufs.schedule.domain.schedule

import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.TimeSlot
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [TodaySchedule] 的"正在进行 / 下一节"判定测试:
 * 覆盖区间内/边界、跨大节、时间段缺失、严格大于与时间解析。
 */
class TodayScheduleTest {

    private val slots = listOf(
        TimeSlot(schemeId = 1L, slotNumber = 1, displayName = "第一大节", startTime = "08:30", endTime = "09:50"),
        TimeSlot(schemeId = 1L, slotNumber = 2, displayName = "第二大节", startTime = "10:10", endTime = "12:15"),
        TimeSlot(schemeId = 1L, slotNumber = 3, displayName = "第三大节", startTime = "12:30", endTime = "13:50"),
    )

    private fun course(id: Long) = Course(id = id, courseTableId = 1L, name = "课程$id")

    private fun entry(id: Long, startSlot: Int, endSlot: Int) = DayCourseEntry(
        course = course(id),
        occurrence = CourseOccurrence(
            id = id,
            courseId = id,
            weekday = 1,
            startSlot = startSlot,
            endSlot = endSlot,
        ),
    )

    @Test
    fun `parseMinutes_解析合法时间`() {
        assertEquals(8 * 60 + 30, TodaySchedule.parseMinutes("08:30"))
        assertEquals(0, TodaySchedule.parseMinutes("00:00"))
        assertEquals(23 * 60 + 59, TodaySchedule.parseMinutes("23:59"))
    }

    @Test
    fun `parseMinutes_非法文本返回null`() {
        assertNull(TodaySchedule.parseMinutes("0830"))
        assertNull(TodaySchedule.parseMinutes("25:00"))
        assertNull(TodaySchedule.parseMinutes("10:70"))
        assertNull(TodaySchedule.parseMinutes("abc"))
    }

    @Test
    fun `正在进行_时间落在区间内`() {
        val entries = listOf(entry(1, 1, 2))
        val found = TodaySchedule.ongoing(entries, slots, LocalTime.of(10, 30))
        assertEquals(1L, found?.course?.id)
    }

    @Test
    fun `正在进行_开始时刻视为进行中`() {
        val entries = listOf(entry(1, 1, 1))
        val found = TodaySchedule.ongoing(entries, slots, LocalTime.of(8, 30))
        assertEquals(1L, found?.course?.id)
    }

    @Test
    fun `正在进行_结束时刻不再进行中`() {
        val entries = listOf(entry(1, 1, 1))
        assertNull(TodaySchedule.ongoing(entries, slots, LocalTime.of(9, 50)))
    }

    @Test
    fun `正在进行_跨大节取首末时间`() {
        val entries = listOf(entry(1, 2, 3))
        val found = TodaySchedule.ongoing(entries, slots, LocalTime.of(13, 0))
        assertEquals(1L, found?.course?.id)
    }

    @Test
    fun `正在进行_时间段缺失返回null`() {
        val entries = listOf(entry(1, 8, 9))
        assertNull(TodaySchedule.ongoing(entries, slots, LocalTime.of(10, 0)))
    }

    @Test
    fun `下一节_开始时间晚于现在`() {
        val entries = listOf(entry(1, 1, 1), entry(2, 2, 2))
        val found = TodaySchedule.next(entries, slots, LocalTime.of(9, 0))
        assertEquals(2L, found?.course?.id)
    }

    @Test
    fun `下一节_与开始时间相同不算下一节`() {
        val entries = listOf(entry(1, 2, 2))
        assertNull(TodaySchedule.next(entries, slots, LocalTime.of(10, 10)))
    }

    @Test
    fun `下一节_无更晚安排返回null`() {
        val entries = listOf(entry(1, 1, 1))
        assertNull(TodaySchedule.next(entries, slots, LocalTime.of(22, 0)))
    }

    @Test
    fun `开始时间文本_正常与缺失`() {
        assertEquals("10:10", TodaySchedule.startTimeOf(entry(1, 2, 2), slots))
        assertNull(TodaySchedule.startTimeOf(entry(1, 9, 9), slots))
    }
}