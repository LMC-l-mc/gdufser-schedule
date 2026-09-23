package com.gdufs.schedule.domain.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 学期周次计算结果,用于区分未开始、学期内第 n 周与已结束。
 */
sealed interface TermWeek {

    /** 学期未开始:日期早于第 1 周首日 */
    data object NotStarted : TermWeek

    /** 学期内第 [week] 周(1 起) */
    data class InTerm(val week: Int) : TermWeek

    /** 学期已结束:周次超过 totalWeeks */
    data object Ended : TermWeek
}

/**
 * 纯 Kotlin 的课表日期与周次计算工具,不依赖 Android 框架,
 * 未来可整体迁入 Kotlin Multiplatform 共享模块。
 *
 * 约定:
 * - 日期以 epoch 毫秒传入,换算时只取日期部分(丢弃时分秒);
 * - 星期编号 1=周一 … 7=周日(与 ISO 星期一致);
 * - 学期开始日所在的那一周为第 1 周,每周的起始日由 firstDayOfWeek 决定。
 *   注意:第 1 周首日可能早于学期开始日。例如周三开学、周一为一周第一天时,
 *   该周周一虽早于开学日,但按周次口径仍属于第 1 周。
 */
object ScheduleCalculator {

    const val MONDAY = 1
    const val SUNDAY = 7

    /** epoch 毫秒 → 当地日期(丢弃时分秒) */
    fun toLocalDate(dateEpochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(dateEpochMillis).atZone(zone).toLocalDate()

    /** 当地日期当天 0 点 → epoch 毫秒 */
    fun toEpochMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** 日期是星期几(1=周一 … 7=周日),与每周第一天的设置无关 */
    fun weekdayOf(dateEpochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        toLocalDate(dateEpochMillis, zone).dayOfWeek.value

    /** 星期编号距每周首日的偏移天数(0..6) */
    private fun offsetFromWeekStart(dayOfWeek: Int, firstDayOfWeek: Int): Int =
        Math.floorMod(dayOfWeek - firstDayOfWeek, 7)

    /**
     * 第 1 周首日:不晚于学期开始日、且为该周每周第一天的日期。
     */
    fun weekOneStartDate(
        termStartEpochMillis: Long,
        firstDayOfWeek: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate {
        require(firstDayOfWeek in MONDAY..SUNDAY) {
            "firstDayOfWeek 必须在 1..7,实际为 $firstDayOfWeek"
        }
        val start = toLocalDate(termStartEpochMillis, zone)
        return start.minusDays(offsetFromWeekStart(start.dayOfWeek.value, firstDayOfWeek).toLong())
    }

    /**
     * 计算日期所在周次(1 起),不判断总周数。
     * 第 1 周首日及其后 7 天内为第 1 周;早于第 1 周首日时返回 0 或负数。
     */
    fun weekNumberOf(
        dateEpochMillis: Long,
        termStartEpochMillis: Long,
        firstDayOfWeek: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val weekOne = weekOneStartDate(termStartEpochMillis, firstDayOfWeek, zone)
        val date = toLocalDate(dateEpochMillis, zone)
        return Math.floorDiv(ChronoUnit.DAYS.between(weekOne, date), 7L).toInt() + 1
    }

    /**
     * 计算日期所处的学期周次,按 totalWeeks 区分未开始与已结束。
     */
    fun weekOfTerm(
        dateEpochMillis: Long,
        termStartEpochMillis: Long,
        firstDayOfWeek: Int,
        totalWeeks: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): TermWeek {
        require(totalWeeks >= 1) { "totalWeeks 必须 >= 1,实际为 $totalWeeks" }
        val raw = weekNumberOf(dateEpochMillis, termStartEpochMillis, firstDayOfWeek, zone)
        return when {
            raw < 1 -> TermWeek.NotStarted
            raw > totalWeeks -> TermWeek.Ended
            else -> TermWeek.InTerm(raw)
        }
    }

    /**
     * 计算第 [week] 周(1 起)、星期 [dayOfWeek](1=周一 … 7=周日)的具体日期,
     * 返回当地 0 点的 epoch 毫秒。
     */
    fun dateOfWeek(
        week: Int,
        dayOfWeek: Int,
        termStartEpochMillis: Long,
        firstDayOfWeek: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        require(week >= 1) { "week 必须 >= 1,实际为 $week" }
        require(dayOfWeek in MONDAY..SUNDAY) { "dayOfWeek 必须在 1..7,实际为 $dayOfWeek" }
        val weekOne = weekOneStartDate(termStartEpochMillis, firstDayOfWeek, zone)
        val date = weekOne.plusDays(
            (week - 1) * 7L + offsetFromWeekStart(dayOfWeek, firstDayOfWeek),
        )
        return toEpochMillis(date, zone)
    }
}