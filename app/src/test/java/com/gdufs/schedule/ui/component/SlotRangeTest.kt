package com.gdufs.schedule.ui.component

import com.gdufs.schedule.data.model.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [slotRangeOf] 的大节区间解析测试:
 * 覆盖单一大节、跨大节、序号缺失与自定义名称场景。
 */
class SlotRangeTest {

    private val slots = listOf(
        TimeSlot(schemeId = 1L, slotNumber = 1, displayName = "第一大节", startTime = "08:30", endTime = "09:50"),
        TimeSlot(schemeId = 1L, slotNumber = 2, displayName = "第二大节", startTime = "10:10", endTime = "12:15"),
        TimeSlot(schemeId = 1L, slotNumber = 3, displayName = "第三大节", startTime = "12:30", endTime = "13:50"),
    )

    @Test
    fun `单一大节_返回完整名称与起止时间`() {
        val range = slotRangeOf(2, 2, slots)

        assertTrue(range!!.single)
        assertEquals("第二大节", range.startName)
        assertEquals("第二大节", range.endName)
        assertEquals("10:10", range.startTime)
        assertEquals("12:15", range.endTime)
    }

    @Test
    fun `跨大节_起止名称与首末时间正确`() {
        val range = slotRangeOf(1, 3, slots)

        assertFalse(range!!.single)
        assertEquals("第一大节", range.startName)
        assertEquals("第三大节", range.endName)
        assertEquals("08:30", range.startTime)
        assertEquals("13:50", range.endTime)
    }

    @Test
    fun `起始大节不存在_返回null`() {
        assertNull(slotRangeOf(9, 2, slots))
    }

    @Test
    fun `结束大节不存在_返回null`() {
        assertNull(slotRangeOf(2, 9, slots))
    }

    @Test
    fun `自定义名称跨大节_保留原名`() {
        val custom = listOf(
            TimeSlot(schemeId = 1L, slotNumber = 1, displayName = "早课", startTime = "08:00", endTime = "09:00"),
            TimeSlot(schemeId = 1L, slotNumber = 2, displayName = "午课", startTime = "12:00", endTime = "13:00"),
        )

        val range = slotRangeOf(1, 2, custom)

        assertEquals("早课", range!!.startName)
        assertEquals("午课", range.endName)
        assertEquals("08:00", range.startTime)
        assertEquals("13:00", range.endTime)
    }
}