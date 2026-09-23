package com.gdufs.schedule.domain.widget

import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.data.model.CourseWithOccurrences
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.data.model.WeekMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 小组件快照构建器测试:
 * 覆盖单双周/自定义周次过滤、下一节判定、跨大节实际时间、
 * 备注标记、空课程与 payload 序列化往返。
 */
class WidgetSnapshotBuilderTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 学期从 2025-09-01(周一)开始,今天固定为第 2 周周一(偶数周) */
    private val termStart: Long = LocalDate.of(2025, 9, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    private val today: LocalDate = LocalDate.of(2025, 9, 8)
    private val todayEpoch: Long = today.atStartOfDay(zone).toInstant().toEpochMilli()

    private val table = CourseTable(
        id = 1L,
        name = "测试学期",
        termStartDate = termStart,
        totalWeeks = 20,
        firstDayOfWeek = 1,
        timeSlotSchemeId = 1L,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private val slots = listOf(
        TimeSlot(schemeId = 1L, slotNumber = 1, displayName = "第一大节", startTime = "08:30", endTime = "09:50"),
        TimeSlot(schemeId = 1L, slotNumber = 2, displayName = "第二大节", startTime = "10:10", endTime = "12:15"),
        TimeSlot(schemeId = 1L, slotNumber = 3, displayName = "第三大节", startTime = "12:30", endTime = "13:50"),
    )

    private fun course(
        id: Long,
        name: String,
        teacher: String = "",
        location: String = "",
        remark: String = "",
    ) = Course(id = id, courseTableId = 1L, name = name, teacher = teacher, location = location, remark = remark)

    private fun occurrence(
        id: Long,
        courseId: Long,
        startSlot: Int,
        endSlot: Int,
        weekMode: WeekMode,
        customWeeksJson: String = "",
    ) = CourseOccurrence(
        id = id,
        courseId = courseId,
        weekday = 1,
        startSlot = startSlot,
        endSlot = endSlot,
        weekMode = weekMode,
        customWeeksJson = customWeeksJson,
    )

    private fun withCourses(vararg pairs: Pair<Course, List<CourseOccurrence>>): List<CourseWithOccurrences> =
        pairs.map { CourseWithOccurrences(course = it.first, occurrences = it.second) }

    @Test
    fun `命中周次规则的课程按开始大节排序`() {
        val content = WidgetSnapshotBuilder.build(
            table = table,
            courses = withCourses(
                // 每周:第一大节(含备注)
                course(1, "高数", teacher = "张老师", location = "A101", remark = "带教材") to
                    listOf(occurrence(1, 1, 2, 2, WeekMode.EVERY_WEEK)),
                // 偶数周(今天是第 2 周):第一大节,跨大节
                course(2, "物理") to listOf(occurrence(2, 2, 1, 2, WeekMode.EVEN_WEEKS)),
                // 奇数周:今天不生效
                course(3, "单周课") to listOf(occurrence(3, 3, 1, 1, WeekMode.ODD_WEEKS)),
                // 自定义周 [3]:今天(第 2 周)不生效
                course(4, "自定义课") to listOf(occurrence(4, 4, 1, 1, WeekMode.CUSTOM, "[3]")),
            ),
            timeSlots = slots,
            dateEpochMillis = todayEpoch,
            now = LocalTime.of(8, 0),
        )

        assertEquals(2, content.items.size)
        assertEquals(listOf("物理", "高数"), content.items.map { it.name })
    }

    @Test
    fun `跨大节_时间范围为起始大节开始到结束大节结束`() {
        val content = WidgetSnapshotBuilder.build(
            table = table,
            courses = withCourses(
                course(2, "物理") to listOf(occurrence(2, 2, 1, 2, WeekMode.EVERY_WEEK)),
            ),
            timeSlots = slots,
            dateEpochMillis = todayEpoch,
            now = LocalTime.of(8, 0),
        )

        val item = content.items.single()
        assertEquals("第一至第二大节", item.slotLabel)
        assertEquals("08:30–12:15", item.timeRange)
    }

    @Test
    fun `备注标记与教师地点透传`() {
        val content = WidgetSnapshotBuilder.build(
            table = table,
            courses = withCourses(
                course(1, "高数", teacher = "张老师", location = "A101", remark = "带教材") to
                    listOf(occurrence(1, 1, 1, 1, WeekMode.EVERY_WEEK)),
                course(2, "英语") to listOf(occurrence(2, 2, 2, 2, WeekMode.EVERY_WEEK)),
            ),
            timeSlots = slots,
            dateEpochMillis = todayEpoch,
            now = LocalTime.of(8, 0),
        )

        val withRemark = content.items.first { it.name == "高数" }
        assertTrue(withRemark.hasRemark)
        assertEquals("张老师", withRemark.teacher)
        assertEquals("A101", withRemark.location)
        assertFalse(content.items.first { it.name == "英语" }.hasRemark)
    }

    @Test
    fun `下一节_开始时间晚于当前的最近课程`() {
        val content8 = WidgetSnapshotBuilder.build(
            table = table,
            courses = withCourses(
                course(1, "高数") to listOf(occurrence(1, 1, 1, 1, WeekMode.EVERY_WEEK)),
                course(2, "英语") to listOf(occurrence(2, 2, 2, 2, WeekMode.EVERY_WEEK)),
            ),
            timeSlots = slots,
            dateEpochMillis = todayEpoch,
            now = LocalTime.of(8, 0),
        )
        assertEquals(0, content8.nextItemIndex) // 下一节 = 高数(08:30)

        val content10 = WidgetSnapshotBuilder.build(
            table = table,
            courses = withCourses(
                course(1, "高数") to listOf(occurrence(1, 1, 1, 1, WeekMode.EVERY_WEEK)),
                course(2, "英语") to listOf(occurrence(2, 2, 2, 2, WeekMode.EVERY_WEEK)),
            ),
            timeSlots = slots,
            dateEpochMillis = todayEpoch,
            now = LocalTime.of(10, 0),
        )
        assertEquals(1, content10.nextItemIndex) // 下一节 = 英语(10:10)

        val contentLater = WidgetSnapshotBuilder.build(
            table = table,
            courses = withCourses(
                course(1, "高数") to listOf(occurrence(1, 1, 1, 1, WeekMode.EVERY_WEEK)),
            ),
            timeSlots = slots,
            dateEpochMillis = todayEpoch,
            now = LocalTime.of(15, 0),
        )
        assertNull(contentLater.nextItemIndex) // 已无下一节
    }

    @Test
    fun `无课程_返回空条目`() {
        val content = WidgetSnapshotBuilder.build(
            table = table,
            courses = emptyList(),
            timeSlots = slots,
            dateEpochMillis = todayEpoch,
            now = LocalTime.of(8, 0),
        )
        assertTrue(content.items.isEmpty())
        assertNull(content.nextItemIndex)
        assertEquals("9月8日", content.dateLabel)
        assertEquals(1, content.weekdayIndex)
    }

    @Test
    fun `payload序列化往返_内容等价`() {
        val content = WidgetSnapshotBuilder.build(
            table = table,
            courses = withCourses(
                course(1, "高数", teacher = "张老师", remark = "备注内容") to
                    listOf(occurrence(1, 1, 1, 2, WeekMode.EVEN_WEEKS)),
            ),
            timeSlots = slots,
            dateEpochMillis = todayEpoch,
            now = LocalTime.of(7, 0),
        )
        val restored = WidgetSnapshotBuilder.fromPayloadJson(WidgetSnapshotBuilder.toPayloadJson(content))
        assertEquals(content, restored)
    }
}