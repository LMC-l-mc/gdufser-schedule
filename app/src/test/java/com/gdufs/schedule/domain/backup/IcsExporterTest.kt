package com.gdufs.schedule.domain.backup

import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.data.model.CourseWithOccurrences
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.data.model.WeekMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * ICS 导出测试:
 * 覆盖含备注/无备注、跨大节实际时间、单双周与自定义周次的事件展开、文本转义。
 * 固定使用 Asia/Shanghai 时区,断言与当地时间无关。
 */
class IcsExporterTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 2025-09-01(周一),firstDayOfWeek=1,第 1 周周一的日期即学期开始日 */
    private val termStart: Long = LocalDate.of(2025, 9, 1).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun table(totalWeeks: Int = 20) = CourseTable(
        id = 1L,
        name = "测试学期",
        termStartDate = termStart,
        totalWeeks = totalWeeks,
        firstDayOfWeek = 1,
        timeSlotSchemeId = 1L,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private val slots = listOf(
        TimeSlot(schemeId = 1L, slotNumber = 1, displayName = "第一大节", startTime = "08:30", endTime = "09:50"),
        TimeSlot(schemeId = 1L, slotNumber = 2, displayName = "第二大节", startTime = "10:10", endTime = "12:15"),
    )

    @Test
    fun `含备注课程_描述包含教师与备注`() {
        val course = Course(
            id = 1L,
            courseTableId = 1L,
            name = "高等数学",
            teacher = "张老师",
            location = "A101",
            remark = "记得带教材",
        )
        val courses = listOf(
            CourseWithOccurrences(
                course = course,
                occurrences = listOf(CourseOccurrence(id = 1L, courseId = 1L, weekday = 1, startSlot = 1, endSlot = 1)),
            )
        )
        val ics = IcsExporter.export(table(2), courses, slots, zone)

        assertTrue(ics.contains("SUMMARY:高等数学"))
        assertTrue(ics.contains("DESCRIPTION:教师: 张老师\\n备注: 记得带教材"))
        assertTrue(ics.contains("LOCATION:A101"))
    }

    @Test
    fun `无备注无教师无地点_事件不含描述与地点`() {
        val course = Course(id = 2L, courseTableId = 1L, name = "大学英语")
        val courses = listOf(
            CourseWithOccurrences(
                course = course,
                occurrences = listOf(CourseOccurrence(id = 2L, courseId = 2L, weekday = 1, startSlot = 1, endSlot = 1)),
            )
        )
        val ics = IcsExporter.export(table(2), courses, slots, zone)

        assertTrue(ics.contains("SUMMARY:大学英语"))
        assertFalse(ics.contains("DESCRIPTION:"))
        assertFalse(ics.contains("LOCATION:"))
    }

    @Test
    fun `跨大节_实际开始时间取首节开始_结束时间取末节结束`() {
        val course = Course(id = 1L, courseTableId = 1L, name = "大学物理")
        val courses = listOf(
            CourseWithOccurrences(
                course = course,
                occurrences = listOf(
                    CourseOccurrence(id = 1L, courseId = 1L, weekday = 1, startSlot = 1, endSlot = 2),
                ),
            )
        )
        val ics = IcsExporter.export(table(2), courses, slots, zone)

        assertTrue(ics.contains("DTSTART;TZID=Asia/Shanghai:20250901T083000"))
        assertTrue(ics.contains("DTEND;TZID=Asia/Shanghai:20250901T121500"))
    }

    @Test
    fun `每周课程_按总周数逐周展开`() {
        val course = Course(id = 1L, courseTableId = 1L, name = "每周课")
        val courses = listOf(
            CourseWithOccurrences(
                course = course,
                occurrences = listOf(
                    CourseOccurrence(id = 1L, courseId = 1L, weekday = 1, startSlot = 1, endSlot = 1, weekMode = WeekMode.EVERY_WEEK),
                ),
            )
        )
        val ics = IcsExporter.export(table(20), courses, slots, zone)
        assertEquals(20, ics.split("BEGIN:VEVENT").size - 1)
    }

    @Test
    fun `单周课程_仅奇数周展开`() {
        val course = Course(id = 1L, courseTableId = 1L, name = "单周课")
        val courses = listOf(
            CourseWithOccurrences(
                course = course,
                occurrences = listOf(
                    CourseOccurrence(id = 1L, courseId = 1L, weekday = 1, startSlot = 1, endSlot = 1, weekMode = WeekMode.ODD_WEEKS),
                ),
            )
        )
        val ics = IcsExporter.export(table(20), courses, slots, zone)
        assertEquals(10, ics.split("BEGIN:VEVENT").size - 1)
    }

    @Test
    fun `双周课程_仅偶数周展开`() {
        val course = Course(id = 1L, courseTableId = 1L, name = "双周课")
        val courses = listOf(
            CourseWithOccurrences(
                course = course,
                occurrences = listOf(
                    CourseOccurrence(id = 1L, courseId = 1L, weekday = 1, startSlot = 1, endSlot = 1, weekMode = WeekMode.EVEN_WEEKS),
                ),
            )
        )
        val ics = IcsExporter.export(table(20), courses, slots, zone)
        assertEquals(10, ics.split("BEGIN:VEVENT").size - 1)
    }

    @Test
    fun `自定义周次课程_仅指定周展开`() {
        val course = Course(id = 1L, courseTableId = 1L, name = "自定义课")
        val courses = listOf(
            CourseWithOccurrences(
                course = course,
                occurrences = listOf(
                    CourseOccurrence(
                        id = 1L,
                        courseId = 1L,
                        weekday = 1,
                        startSlot = 1,
                        endSlot = 1,
                        weekMode = WeekMode.CUSTOM,
                        customWeeksJson = "[2,5]",
                    ),
                ),
            )
        )
        val ics = IcsExporter.export(table(20), courses, slots, zone)
        assertEquals(2, ics.split("BEGIN:VEVENT").size - 1)
    }

    @Test
    fun `文本转义_逗号分号反斜杠`() {
        val course = Course(id = 1L, courseTableId = 1L, name = "数学,分析;进阶")
        val courses = listOf(
            CourseWithOccurrences(
                course = course,
                occurrences = listOf(CourseOccurrence(id = 1L, courseId = 1L, weekday = 1, startSlot = 1, endSlot = 1)),
            )
        )
        val ics = IcsExporter.export(table(1), courses, slots, zone)
        assertTrue(ics.contains("SUMMARY:数学\\,分析\\;进阶"))
    }

    @Test
    fun `时间段缺失_该安排跳过不生成事件`() {
        val course = Course(id = 1L, courseTableId = 1L, name = "无时间段")
        val courses = listOf(
            CourseWithOccurrences(
                course = course,
                occurrences = listOf(CourseOccurrence(id = 1L, courseId = 1L, weekday = 1, startSlot = 9, endSlot = 9)),
            )
        )
        val ics = IcsExporter.export(table(2), courses, slots, zone)
        assertEquals(0, ics.split("BEGIN:VEVENT").size - 1)
    }

    @Test
    fun `时间文本解析_合法与非法`() {
        assertEquals("083000", IcsExporter.toIcsTime("08:30"))
        assertEquals("235900", IcsExporter.toIcsTime("23:59"))
        assertNull(IcsExporter.toIcsTime("0830"))
        assertNull(IcsExporter.toIcsTime("25:00"))
        assertNull(IcsExporter.toIcsTime("abc"))
    }
}