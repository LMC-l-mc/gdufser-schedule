package com.gdufs.schedule.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 日期与周次换算的边界测试。
 * 学期样例 1:2026-02-23(周一)开学,共 16 周。
 * 学期样例 2:2026-02-25(周三)开学,用于验证开学日不是周一时第 1 周的起算方式。
 * 测试统一使用 UTC 时区保证结果确定。
 */
class ScheduleCalculatorTest {

    private val zone = ZoneOffset.UTC

    private fun ms(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli()

    private val termStartMonday = ms(2026, 2, 23)

    @Test
    fun `学期开始日为周一_当天为第1周`() {
        assertEquals(1, ScheduleCalculator.weekNumberOf(termStartMonday, termStartMonday, 1, zone))
        assertEquals(
            TermWeek.InTerm(1),
            ScheduleCalculator.weekOfTerm(termStartMonday, termStartMonday, 1, 16, zone),
        )
    }

    @Test
    fun `学期开始日不是周一_与开学日同周的周一也算第1周`() {
        val termStartWednesday = ms(2026, 2, 25)
        // 第 1 周首日为周一 2026-02-23(早于开学日两天)
        assertEquals(LocalDate.of(2026, 2, 23), ScheduleCalculator.weekOneStartDate(termStartWednesday, 1, zone))
        assertEquals(TermWeek.InTerm(1), ScheduleCalculator.weekOfTerm(ms(2026, 2, 23), termStartWednesday, 1, 16, zone))
        assertEquals(TermWeek.InTerm(1), ScheduleCalculator.weekOfTerm(ms(2026, 2, 25), termStartWednesday, 1, 16, zone))
        // 第 1 周最后一天:周日 2026-03-01
        assertEquals(TermWeek.InTerm(1), ScheduleCalculator.weekOfTerm(ms(2026, 3, 1), termStartWednesday, 1, 16, zone))
        // 下周一 2026-03-02 → 第 2 周
        assertEquals(TermWeek.InTerm(2), ScheduleCalculator.weekOfTerm(ms(2026, 3, 2), termStartWednesday, 1, 16, zone))
    }

    @Test
    fun `学期未开始_第1周首日之前返回NotStarted`() {
        val dayBeforeTerm = ms(2026, 2, 22) // 第 1 周首日(02-23)的前一天
        assertEquals(0, ScheduleCalculator.weekNumberOf(dayBeforeTerm, termStartMonday, 1, zone))
        assertEquals(TermWeek.NotStarted, ScheduleCalculator.weekOfTerm(dayBeforeTerm, termStartMonday, 1, 16, zone))
        // 更早的日期周次为负数
        assertEquals(-1, ScheduleCalculator.weekNumberOf(ms(2026, 2, 15), termStartMonday, 1, zone))
    }

    @Test
    fun `学期已结束_超过总周数返回Ended`() {
        // 第 16 周:2026-06-08(周一)…2026-06-14(周日)
        assertEquals(TermWeek.InTerm(16), ScheduleCalculator.weekOfTerm(ms(2026, 6, 14), termStartMonday, 1, 16, zone))
        assertEquals(TermWeek.Ended, ScheduleCalculator.weekOfTerm(ms(2026, 6, 15), termStartMonday, 1, 16, zone))
    }

    @Test
    fun `每周第一天为周日时_周次从周日算起`() {
        val termStartWednesday = ms(2026, 2, 25)
        // 第 1 周首日为之前的周日 2026-02-22
        assertEquals(LocalDate.of(2026, 2, 22), ScheduleCalculator.weekOneStartDate(termStartWednesday, 7, zone))
        assertEquals(1, ScheduleCalculator.weekNumberOf(ms(2026, 2, 22), termStartWednesday, 7, zone))
        assertEquals(TermWeek.NotStarted, ScheduleCalculator.weekOfTerm(ms(2026, 2, 21), termStartWednesday, 7, 16, zone))
        // 下一个周日 2026-03-01 → 第 2 周
        assertEquals(TermWeek.InTerm(2), ScheduleCalculator.weekOfTerm(ms(2026, 3, 1), termStartWednesday, 7, 16, zone))
    }

    @Test
    fun `根据周次与星期计算具体日期`() {
        assertEquals(LocalDate.of(2026, 2, 23), date(ScheduleCalculator.dateOfWeek(1, 1, termStartMonday, 1, zone)))
        // 第 3 周、周三(星期编号 3)
        assertEquals(LocalDate.of(2026, 3, 11), date(ScheduleCalculator.dateOfWeek(3, 3, termStartMonday, 1, zone)))
        // 第 16 周、周日(星期编号 7)
        assertEquals(LocalDate.of(2026, 6, 14), date(ScheduleCalculator.dateOfWeek(16, 7, termStartMonday, 1, zone)))
    }

    @Test
    fun `每周第一天为周日时_按周次与星期计算日期`() {
        val termStartWednesday = ms(2026, 2, 25)
        // 第 1 周(周日 02-22 起):周一为 02-23,周六为 02-28
        assertEquals(LocalDate.of(2026, 2, 23), date(ScheduleCalculator.dateOfWeek(1, 1, termStartWednesday, 7, zone)))
        assertEquals(LocalDate.of(2026, 2, 28), date(ScheduleCalculator.dateOfWeek(1, 6, termStartWednesday, 7, zone)))
    }

    @Test
    fun `日期与周次计算的往返一致性`() {
        for (week in 1..16) {
            for (dayOfWeek in 1..7) {
                val dateMillis = ScheduleCalculator.dateOfWeek(week, dayOfWeek, termStartMonday, 1, zone)
                assertEquals(week, ScheduleCalculator.weekNumberOf(dateMillis, termStartMonday, 1, zone))
                assertEquals(dayOfWeek, ScheduleCalculator.weekdayOf(dateMillis, zone))
            }
        }
    }

    @Test
    fun `周次计算只取日期部分_忽略时分秒`() {
        // 周一 23:00(UTC):按 UTC 仍属于开学日所在的第 1 周
        val withTime = termStartMonday + 23 * 60 * 60 * 1000L
        assertEquals(TermWeek.InTerm(1), ScheduleCalculator.weekOfTerm(withTime, termStartMonday, 1, 16, zone))
    }

    private fun date(epochMillis: Long): LocalDate = ScheduleCalculator.toLocalDate(epochMillis, zone)
}