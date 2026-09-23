package com.gdufs.schedule.ui.screen.timetable

import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.domain.schedule.WeekCourseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [layoutDayColumn] 网格定位算法测试:
 * 覆盖行定位、跨大节行数、重叠车道分配、越界裁剪与丢弃。
 */
class TimetableLayoutTest {

    private val firstSlotNumber = 1
    private val slotCount = 7

    private fun course(id: Long) = Course(id = id, courseTableId = 1L, name = "课程$id")

    private fun entry(id: Long, weekday: Int, startSlot: Int, endSlot: Int) = WeekCourseEntry(
        dateEpochMillis = 0L,
        weekday = weekday,
        course = course(id),
        occurrence = CourseOccurrence(
            id = id,
            courseId = id,
            weekday = weekday,
            startSlot = startSlot,
            endSlot = endSlot,
        ),
    )

    @Test
    fun `单一课程_定位到对应行且独占整列`() {
        val placed = layoutDayColumn(listOf(entry(1, 1, 3, 3)), firstSlotNumber, slotCount)

        assertEquals(1, placed.size)
        val p = placed.first()
        assertEquals(0, p.lane)
        assertEquals(1, p.laneCount)
        assertEquals(2, p.startRow)
        assertEquals(1, p.rowSpan)
    }

    @Test
    fun `跨大节课程_行数等于跨节数`() {
        val placed = layoutDayColumn(listOf(entry(1, 1, 2, 4)), firstSlotNumber, slotCount)

        val p = placed.first()
        assertEquals(1, p.startRow)
        assertEquals(3, p.rowSpan)
    }

    @Test
    fun `两条不重叠安排_共用同一车道`() {
        val placed = layoutDayColumn(
            listOf(entry(1, 1, 1, 1), entry(2, 1, 4, 5)),
            firstSlotNumber,
            slotCount,
        )

        assertEquals(2, placed.size)
        assertTrue(placed.all { it.lane == 0 })
        assertTrue(placed.all { it.laneCount == 1 })
        assertEquals(0, placed[0].startRow)
        assertEquals(3, placed[1].startRow)
    }

    @Test
    fun `两条重叠安排_分到不同车道`() {
        val placed = layoutDayColumn(
            listOf(entry(1, 1, 1, 2), entry(2, 1, 2, 3)),
            firstSlotNumber,
            slotCount,
        )

        assertEquals(2, placed.size)
        val laneOf = placed.associate { it.entry.course.id to it.lane }
        assertEquals(setOf(0, 1), laneOf.values.toSet())
        assertTrue(placed.all { it.laneCount == 2 })
    }

    @Test
    fun `三条安排中两条重叠_车道复用`() {
        // A 1-2、B 2-3 重叠;B 与 C 4-4 不重叠,C 复用 A 所在车道
        val placed = layoutDayColumn(
            listOf(entry(1, 1, 1, 2), entry(2, 1, 2, 3), entry(3, 1, 4, 4)),
            firstSlotNumber,
            slotCount,
        )

        assertEquals(3, placed.size)
        val lanes = placed.associate { it.entry.course.id to it.lane }
        assertEquals(0, lanes[1L])
        assertEquals(1, lanes[2L])
        assertEquals(0, lanes[3L])
        assertTrue(placed.all { it.laneCount == 2 })
    }

    @Test
    fun `结束大节超出作息范围_裁剪行数`() {
        val placed = layoutDayColumn(listOf(entry(1, 1, 7, 9)), firstSlotNumber, slotCount)

        val p = placed.first()
        assertEquals(6, p.startRow)
        assertEquals(1, p.rowSpan)
    }

    @Test
    fun `完全超出作息范围_安排被丢弃`() {
        val placed = layoutDayColumn(
            listOf(entry(1, 1, 8, 9), entry(2, 1, 1, 1)),
            firstSlotNumber,
            slotCount,
        )

        assertEquals(1, placed.size)
        assertEquals(2L, placed.first().entry.course.id)
    }
}