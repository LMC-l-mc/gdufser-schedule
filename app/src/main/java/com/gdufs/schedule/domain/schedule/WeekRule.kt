package com.gdufs.schedule.domain.schedule

import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.WeekMode

/**
 * 课程周次规则:判断上课安排在给定周次是否生效。
 * 纯 Kotlin 实现(依赖的 WeekMode 为纯枚举),不依赖 Android 框架。
 */
object WeekRule {

    private val CUSTOM_WEEKS_REGEX = Regex("""^\[\s*(\d+(?:\s*,\s*\d+)*)?\s*]$""")

    /**
     * 解析自定义周次 JSON 数组字符串,例如 "[1,2,5]" → setOf(1, 2, 5)。
     * 空串、"[]" 或格式非法时返回空集合(即不匹配任何周次)。
     */
    fun parseCustomWeeks(json: String): Set<Int> {
        val text = json.trim()
        if (text.isEmpty()) return emptySet()
        val match = CUSTOM_WEEKS_REGEX.matchEntire(text) ?: return emptySet()
        val body = match.groupValues[1]
        if (body.isBlank()) return emptySet()
        return body.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    /**
     * 把生效周次集合序列化为 JSON 数组字符串,例如 setOf(1, 2, 5) → "[1,2,5]"。
     * 空集合返回空串。输出可被 [parseCustomWeeks] 无损解析。
     */
    fun formatCustomWeeks(weeks: Set<Int>): String {
        if (weeks.isEmpty()) return ""
        return weeks.sorted().joinToString(prefix = "[", postfix = "]", separator = ",")
    }

    /**
     * 判断周次模式在第 [week] 周(1 起)是否生效:
     * 每周恒生效;单周只匹配奇数周;双周只匹配偶数周;自定义仅匹配列表内周次。
     */
    fun isActiveInWeek(weekMode: WeekMode, customWeeks: Set<Int>, week: Int): Boolean =
        when (weekMode) {
            WeekMode.EVERY_WEEK -> true
            WeekMode.ODD_WEEKS -> week % 2 == 1
            WeekMode.EVEN_WEEKS -> week % 2 == 0
            WeekMode.CUSTOM -> week in customWeeks
        }
}

/**
 * 上课安排在第 [week] 周(1 起)是否生效,自动解析其 customWeeksJson。
 */
fun CourseOccurrence.isActiveInWeek(week: Int): Boolean =
    WeekRule.isActiveInWeek(weekMode, WeekRule.parseCustomWeeks(customWeeksJson), week)