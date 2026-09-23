package com.gdufs.schedule.domain.schedule

import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.WeekMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 周次规则测试:每周、单周、双周、自定义周次及 JSON 解析边界。
 */
class WeekRuleTest {

    @Test
    fun `每周模式对所有周次生效`() {
        for (week in 1..20) {
            assertTrue(WeekRule.isActiveInWeek(WeekMode.EVERY_WEEK, emptySet(), week))
        }
    }

    @Test
    fun `单周模式仅奇数周生效`() {
        assertTrue(WeekRule.isActiveInWeek(WeekMode.ODD_WEEKS, emptySet(), 1))
        assertTrue(WeekRule.isActiveInWeek(WeekMode.ODD_WEEKS, emptySet(), 5))
        assertTrue(WeekRule.isActiveInWeek(WeekMode.ODD_WEEKS, emptySet(), 15))
        assertFalse(WeekRule.isActiveInWeek(WeekMode.ODD_WEEKS, emptySet(), 2))
        assertFalse(WeekRule.isActiveInWeek(WeekMode.ODD_WEEKS, emptySet(), 16))
    }

    @Test
    fun `双周模式仅偶数周生效`() {
        assertTrue(WeekRule.isActiveInWeek(WeekMode.EVEN_WEEKS, emptySet(), 2))
        assertTrue(WeekRule.isActiveInWeek(WeekMode.EVEN_WEEKS, emptySet(), 16))
        assertFalse(WeekRule.isActiveInWeek(WeekMode.EVEN_WEEKS, emptySet(), 1))
        assertFalse(WeekRule.isActiveInWeek(WeekMode.EVEN_WEEKS, emptySet(), 15))
    }

    @Test
    fun `自定义周次仅列表内周次生效`() {
        val custom = setOf(2, 4, 5)
        assertTrue(WeekRule.isActiveInWeek(WeekMode.CUSTOM, custom, 2))
        assertTrue(WeekRule.isActiveInWeek(WeekMode.CUSTOM, custom, 4))
        assertTrue(WeekRule.isActiveInWeek(WeekMode.CUSTOM, custom, 5))
        assertFalse(WeekRule.isActiveInWeek(WeekMode.CUSTOM, custom, 1))
        assertFalse(WeekRule.isActiveInWeek(WeekMode.CUSTOM, custom, 3))
        assertFalse(WeekRule.isActiveInWeek(WeekMode.CUSTOM, custom, 6))
    }

    @Test
    fun `解析自定义周次JSON数组`() {
        assertEquals(setOf(1, 2, 5), WeekRule.parseCustomWeeks("[1,2,5]"))
        assertEquals(setOf(3), WeekRule.parseCustomWeeks("[3]"))
        assertEquals(setOf(1, 2), WeekRule.parseCustomWeeks(" [ 1 , 2 ] "))
        assertEquals(setOf(1), WeekRule.parseCustomWeeks("[1,1]")) // 重复去重
        assertEquals(emptySet<Int>(), WeekRule.parseCustomWeeks("[]"))
        assertEquals(emptySet<Int>(), WeekRule.parseCustomWeeks(""))
        assertEquals(emptySet<Int>(), WeekRule.parseCustomWeeks("   "))
    }

    @Test
    fun `解析非法自定义周次返回空集合`() {
        assertEquals(emptySet<Int>(), WeekRule.parseCustomWeeks("[1,a]"))
        assertEquals(emptySet<Int>(), WeekRule.parseCustomWeeks("1,2"))
        assertEquals(emptySet<Int>(), WeekRule.parseCustomWeeks("[1,2"))
        assertEquals(emptySet<Int>(), WeekRule.parseCustomWeeks("[99999999999999999999]")) // 超出 Int 范围
    }

    @Test
    fun `上课安排自动解析customWeeksJson判断周次生效`() {
        val custom = occurrence(weekday = 1, start = 1, mode = WeekMode.CUSTOM, custom = "[2,4]")
        assertTrue(custom.isActiveInWeek(2))
        assertTrue(custom.isActiveInWeek(4))
        assertFalse(custom.isActiveInWeek(1))
        assertFalse(custom.isActiveInWeek(3))

        val odd = occurrence(weekday = 1, start = 1, mode = WeekMode.ODD_WEEKS)
        assertTrue(odd.isActiveInWeek(5))
        assertFalse(odd.isActiveInWeek(4))

        val every = occurrence(weekday = 1, start = 1, mode = WeekMode.EVERY_WEEK)
        assertTrue(every.isActiveInWeek(7))
    }

    private fun occurrence(
        weekday: Int,
        start: Int,
        mode: WeekMode,
        custom: String = "",
        end: Int = start,
    ) = CourseOccurrence(
        courseId = 0L,
        weekday = weekday,
        startSlot = start,
        endSlot = end,
        weekMode = mode,
        customWeeksJson = custom,
    )
}