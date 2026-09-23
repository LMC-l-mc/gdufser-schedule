package com.gdufs.schedule.domain.validation

import com.gdufs.schedule.data.model.TimeSlot

/**
 * 时间段(大节)表单校验错误。
 * UI 层将错误码映射为字符串资源展示;本类型保持纯 Kotlin,便于单元测试与后续 KMP 迁移。
 */
enum class TimeSlotError {
    /** 开始时间缺失或格式不是 HH:mm(24 小时制) */
    START_INVALID_FORMAT,

    /** 结束时间缺失或格式不是 HH:mm(24 小时制) */
    END_INVALID_FORMAT,

    /** 开始时间必须早于结束时间 */
    START_NOT_BEFORE_END,
}

/**
 * 时间段表单校验器(纯 Kotlin)。
 * 规则:
 * 1. 时间格式严格为 HH:mm,小时 00-23、分钟 00-59;
 * 2. 开始时间必须早于结束时间;
 * 3. 新时间段与已有时间段不得重叠(结束时间等于相邻段开始时间视为合法,即允许相接)。
 */
object TimeSlotValidator {

    private val TIME_REGEX = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")

    /** 解析 HH:mm 为当天分钟数;格式非法返回 null */
    fun parseMinutes(text: String): Int? {
        val match = TIME_REGEX.matchEntire(text.trim()) ?: return null
        val hours = match.groupValues[1].toInt()
        val minutes = match.groupValues[2].toInt()
        return hours * 60 + minutes
    }

    /**
     * 校验开始/结束时间的格式与先后关系,返回全部错误(可能同时有格式与先后错误)。
     */
    fun validateRange(startTime: String, endTime: String): List<TimeSlotError> {
        val errors = mutableListOf<TimeSlotError>()
        val start = parseMinutes(startTime)
        val end = parseMinutes(endTime)
        if (start == null) errors += TimeSlotError.START_INVALID_FORMAT
        if (end == null) errors += TimeSlotError.END_INVALID_FORMAT
        if (start != null && end != null && start >= end) {
            errors += TimeSlotError.START_NOT_BEFORE_END
        }
        return errors
    }

    /**
     * 找出与新时间段 [startMinutes, endMinutes) 重叠的已有时间段。
     * 允许相接:已有段结束时间 == 新段开始,或新段结束 == 已有段开始,均不算重叠。
     * [existing] 必须为已校验合法的列表;忽略时间格式非法的条目。
     */
    fun findOverlaps(
        startMinutes: Int,
        endMinutes: Int,
        existing: List<TimeSlot>,
    ): List<TimeSlot> = existing.filter { slot ->
        val slotStart = parseMinutes(slot.startTime) ?: return@filter false
        val slotEnd = parseMinutes(slot.endTime) ?: return@filter false
        startMinutes < slotEnd && slotStart < endMinutes
    }
}