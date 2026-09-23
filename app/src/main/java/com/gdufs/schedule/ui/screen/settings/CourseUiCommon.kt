package com.gdufs.schedule.ui.screen.settings

import com.gdufs.schedule.R
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.data.model.WeekMode

/** 课程卡片可选颜色色板(ARGB),首个为默认色 */
val COURSE_COLOR_OPTIONS: List<Int> = listOf(
    0xFF4C6EF5.toInt(), // 靛蓝(默认)
    0xFF12B886.toInt(), // 青绿
    0xFFFAB005.toInt(), // 明黄
    0xFFFA5252.toInt(), // 珊瑚红
    0xFF7950F2.toInt(), // 紫
    0xFF15AABF.toInt(), // 青
    0xFFFD7E14.toInt(), // 橙
    0xFFE64980.toInt(), // 粉红
)

/** 周次规则对应的字符串资源 id */
fun weekModeLabelRes(mode: WeekMode): Int = when (mode) {
    WeekMode.EVERY_WEEK -> R.string.week_mode_every
    WeekMode.ODD_WEEKS -> R.string.week_mode_odd
    WeekMode.EVEN_WEEKS -> R.string.week_mode_even
    WeekMode.CUSTOM -> R.string.week_mode_custom
}

/** 大节选项完整文本,例如“第一大节（08:30–09:50）” */
fun slotOptionLabel(slot: TimeSlot, format: (String, String, String) -> String): String =
    format(slot.displayName, slot.startTime, slot.endTime)

/**
 * 上课安排的人类可读描述,例如“周一 第一大节–第二大节 每周”。
 * [weekdayNames] 下标 0 对应周一;[weekModeLabel] 为普通(非 Composable)标签函数。
 */
fun describeOccurrence(
    occurrence: CourseOccurrence,
    weekdayNames: Array<String>,
    slots: List<TimeSlot>,
    weekModeLabel: (WeekMode) -> String,
): String {
    val weekday = weekdayNames.getOrElse(occurrence.weekday - 1) { "" }
    val startName = slots.firstOrNull { it.slotNumber == occurrence.startSlot }?.displayName
        ?: occurrence.startSlot.toString()
    val endName = slots.firstOrNull { it.slotNumber == occurrence.endSlot }?.displayName
        ?: occurrence.endSlot.toString()
    val slotRange = if (occurrence.startSlot == occurrence.endSlot) {
        startName
    } else {
        "$startName–$endName"
    }
    val weekRule = when (occurrence.weekMode) {
        WeekMode.EVERY_WEEK -> weekModeLabel(WeekMode.EVERY_WEEK)
        WeekMode.ODD_WEEKS -> weekModeLabel(WeekMode.ODD_WEEKS)
        WeekMode.EVEN_WEEKS -> weekModeLabel(WeekMode.EVEN_WEEKS)
        WeekMode.CUSTOM -> weekModeLabel(WeekMode.CUSTOM) +
            com.gdufs.schedule.domain.schedule.WeekRule.parseCustomWeeks(occurrence.customWeeksJson)
                .sorted()
                .joinToString(prefix = "(", postfix = ")", separator = ",")
    }
    return "$weekday $slotRange $weekRule"
}