package com.gdufs.schedule.domain.validation

import com.gdufs.schedule.data.model.WeekMode

/**
 * 课程表单校验错误。
 * UI 层将错误码映射为字符串资源展示;本类型保持纯 Kotlin,便于单元测试与后续 KMP 迁移。
 */
enum class CourseFormError {
    /** 课程名称为空或全空白 */
    NAME_BLANK,

    /** 至少需要一条上课安排 */
    NO_OCCURRENCE,

    /** 星期不在 1..7(1=周一 … 7=周日) */
    WEEKDAY_INVALID,

    /** 大节序号超出 1..maxSlot 范围(基于作息方案实际大节数) */
    SLOT_OUT_OF_RANGE,

    /** 开始大节晚于结束大节 */
    SLOT_ORDER_INVALID,

    /** 自定义周次模式下未选择任何生效周次 */
    CUSTOM_WEEKS_EMPTY,
}

/** 表单中一条上课安排的草稿(尚未持久化) */
data class OccurrenceDraft(
    val weekday: Int,
    val startSlot: Int,
    val endSlot: Int,
    val weekMode: WeekMode,
    val customWeeks: Set<Int>,
)

/**
 * 课程表单校验器(纯 Kotlin)。
 * 课程名称必填;必须至少一条上课安排;
 * 每条安排星期合法、大节区间在方案范围内且开始不晚于结束;
 * 自定义周次模式必须选择至少一个生效周次。
 * [maxSlot] 为作息方案的大节数量(所有大节序号必须落在 1..maxSlot)。
 */
object CourseFormValidator {

    fun validate(
        name: String,
        occurrences: List<OccurrenceDraft>,
        maxSlot: Int,
    ): List<CourseFormError> {
        val errors = mutableListOf<CourseFormError>()

        if (name.isBlank()) errors += CourseFormError.NAME_BLANK
        if (occurrences.isEmpty()) errors += CourseFormError.NO_OCCURRENCE

        occurrences.forEach { occurrence ->
            if (occurrence.weekday !in 1..7) {
                errors += CourseFormError.WEEKDAY_INVALID
            }
            if (occurrence.startSlot < 1 || occurrence.startSlot > maxSlot ||
                occurrence.endSlot < 1 || occurrence.endSlot > maxSlot
            ) {
                errors += CourseFormError.SLOT_OUT_OF_RANGE
            }
            if (occurrence.startSlot > occurrence.endSlot) {
                errors += CourseFormError.SLOT_ORDER_INVALID
            }
            if (occurrence.weekMode == WeekMode.CUSTOM && occurrence.customWeeks.isEmpty()) {
                errors += CourseFormError.CUSTOM_WEEKS_EMPTY
            }
        }

        return errors
    }
}