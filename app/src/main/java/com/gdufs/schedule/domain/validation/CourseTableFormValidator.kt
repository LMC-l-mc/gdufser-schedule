package com.gdufs.schedule.domain.validation

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 课表表单校验错误。
 * UI 层将错误码映射为字符串资源展示;本类型保持纯 Kotlin,便于单元测试与后续 KMP 迁移。
 */
enum class CourseTableFormError {
    /** 名称为空或全空白 */
    NAME_BLANK,

    /** 学期开始日期为空 */
    DATE_BLANK,

    /** 学期开始日期不是 yyyy-MM-dd 格式 */
    DATE_INVALID_FORMAT,

    /** 学期开始日期无法解析为合法日期(如 2026-13-40) */
    DATE_INVALID_VALUE,

    /** 总周数为空 */
    TOTAL_WEEKS_BLANK,

    /** 总周数不是整数 */
    TOTAL_WEEKS_INVALID,

    /** 总周数超出 1..MAX_TOTAL_WEEKS 范围 */
    TOTAL_WEEKS_OUT_OF_RANGE,

    /** 每周第一天不在 1..7 */
    FIRST_DAY_INVALID,

    /** 未选择作息方案 */
    SCHEME_NOT_SELECTED,
}

/**
 * 课表表单校验器(纯 Kotlin)。
 * 规则:
 * 1. 名称非空;
 * 2. 学期开始日期为严格 yyyy-MM-dd 的合法日期;
 * 3. 总周数为 1..MAX_TOTAL_WEEKS 的整数;
 * 4. 每周第一天为 1(周一)..7(周日);
 * 5. 必须选择作息方案。
 */
object CourseTableFormValidator {

    const val MIN_TOTAL_WEEKS = 1
    const val MAX_TOTAL_WEEKS = 60

    private val DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")

    /** 严格解析 yyyy-MM-dd;格式或日期值非法返回 null */
    fun parseDate(text: String): LocalDate? {
        val trimmed = text.trim()
        if (!DATE_REGEX.matches(trimmed)) return null
        return try {
            LocalDate.parse(trimmed)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** 严格解析整数;格式或日期值非法返回 null */
    fun parseWeeks(text: String): Int? = text.trim().toIntOrNull()

    /** 校验全部字段,返回错误列表(空列表表示校验通过) */
    fun validate(
        name: String,
        dateText: String,
        totalWeeksText: String,
        firstDayOfWeek: Int,
        schemeId: Long?,
    ): List<CourseTableFormError> {
        val errors = mutableListOf<CourseTableFormError>()

        if (name.isBlank()) errors += CourseTableFormError.NAME_BLANK

        val dateTextTrimmed = dateText.trim()
        when {
            dateTextTrimmed.isEmpty() -> errors += CourseTableFormError.DATE_BLANK
            !DATE_REGEX.matches(dateTextTrimmed) -> errors += CourseTableFormError.DATE_INVALID_FORMAT
            parseDate(dateTextTrimmed) == null -> errors += CourseTableFormError.DATE_INVALID_VALUE
        }

        val weeksText = totalWeeksText.trim()
        when {
            weeksText.isEmpty() -> errors += CourseTableFormError.TOTAL_WEEKS_BLANK
            weeksText.toIntOrNull() == null -> errors += CourseTableFormError.TOTAL_WEEKS_INVALID
            else -> {
                val weeks = weeksText.toInt()
                if (weeks !in MIN_TOTAL_WEEKS..MAX_TOTAL_WEEKS) {
                    errors += CourseTableFormError.TOTAL_WEEKS_OUT_OF_RANGE
                }
            }
        }

        if (firstDayOfWeek !in 1..7) {
            errors += CourseTableFormError.FIRST_DAY_INVALID
        }

        if (schemeId == null || schemeId <= 0L) {
            errors += CourseTableFormError.SCHEME_NOT_SELECTED
        }

        return errors
    }
}