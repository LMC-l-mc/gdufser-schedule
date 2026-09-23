package com.gdufs.schedule.domain.validation

import com.gdufs.schedule.data.model.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时间段表单校验器测试:时间格式、开始早于结束、相邻不重叠。
 */
class TimeSlotValidatorTest {

    private fun slot(number: Int, start: String, end: String) = TimeSlot(
        schemeId = 1L,
        slotNumber = number,
        displayName = "第${number}大节",
        startTime = start,
        endTime = end,
    )

    // ---------- 格式解析 ----------

    @Test
    fun `解析合法HHmm时间`() {
        assertEquals(8 * 60 + 30, TimeSlotValidator.parseMinutes("08:30"))
        assertEquals(0, TimeSlotValidator.parseMinutes("00:00"))
        assertEquals(23 * 60 + 59, TimeSlotValidator.parseMinutes("23:59"))
    }

    @Test
    fun `解析非法时间返回null`() {
        assertNull(TimeSlotValidator.parseMinutes(""))
        assertNull(TimeSlotValidator.parseMinutes("8:30"))      // 缺前导零
        assertNull(TimeSlotValidator.parseMinutes("08-30"))     // 分隔符错误
        assertNull(TimeSlotValidator.parseMinutes("24:00"))     // 小时越界
        assertNull(TimeSlotValidator.parseMinutes("12:60"))     // 分钟越界
        assertNull(TimeSlotValidator.parseMinutes("abc"))
    }

    // ---------- 开始早于结束 ----------

    @Test
    fun `开始时间必须早于结束时间`() {
        assertEquals(emptyList<TimeSlotError>(), TimeSlotValidator.validateRange("08:30", "09:50"))
        assertEquals(
            listOf(TimeSlotError.START_NOT_BEFORE_END),
            TimeSlotValidator.validateRange("09:50", "08:30"),
        )
        assertEquals(
            listOf(TimeSlotError.START_NOT_BEFORE_END),
            TimeSlotValidator.validateRange("08:30", "08:30"), // 相等同样非法
        )
    }

    @Test
    fun `格式非法时报告格式错误`() {
        assertEquals(
            listOf(TimeSlotError.START_INVALID_FORMAT),
            TimeSlotValidator.validateRange("9:00", "08:30"),
        )
        assertEquals(
            listOf(TimeSlotError.END_INVALID_FORMAT),
            TimeSlotValidator.validateRange("08:30", "9:50"),
        )
    }

    // ---------- 相邻不重叠 ----------

    @Test
    fun `与已有时间段相接允许_相交重叠报错`() {
        val existing = listOf(
            slot(1, "08:30", "09:50"),
            slot(2, "10:10", "12:15"),
        )

        // 插入在 1、2 之间,与前段相接(09:50 结束 = 09:50 开始)
        assertEquals(
            emptyList<TimeSlot>(),
            TimeSlotValidator.findOverlaps(
                TimeSlotValidator.parseMinutes("09:50")!!,
                TimeSlotValidator.parseMinutes("10:10")!!,
                existing,
            ),
        )

        // 与第 1 大节部分重叠
        assertEquals(
            listOf(existing[0]),
            TimeSlotValidator.findOverlaps(
                TimeSlotValidator.parseMinutes("09:00")!!,
                TimeSlotValidator.parseMinutes("10:00")!!,
                existing,
            ),
        )

        // 完全包含第 2 大节(不与第 1 大节重叠)
        assertEquals(
            listOf(existing[1]),
            TimeSlotValidator.findOverlaps(
                TimeSlotValidator.parseMinutes("10:00")!!,
                TimeSlotValidator.parseMinutes("13:00")!!,
                existing,
            ),
        )
    }

    @Test
    fun `重叠检测基于严格开区间`() {
        val existing = listOf(slot(1, "08:30", "09:50"))
        // 新段结束 == 已有段开始:08:00-08:30 不重叠
        assertTrue(
            TimeSlotValidator.findOverlaps(
                TimeSlotValidator.parseMinutes("08:00")!!,
                TimeSlotValidator.parseMinutes("08:30")!!,
                existing,
            ).isEmpty()
        )
        // 新段开始 == 已有段结束:09:50-10:30 不重叠
        assertTrue(
            TimeSlotValidator.findOverlaps(
                TimeSlotValidator.parseMinutes("09:50")!!,
                TimeSlotValidator.parseMinutes("10:30")!!,
                existing,
            ).isEmpty()
        )
    }
}