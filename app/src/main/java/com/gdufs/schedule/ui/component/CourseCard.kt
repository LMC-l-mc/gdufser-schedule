package com.gdufs.schedule.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gdufs.schedule.R
import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.TimeSlot

/** 大节默认显示名称的"大节"后缀,跨大节标签需去除起始名称后缀 */
private const val SLOT_NAME_SUFFIX = "大节"

/** 卡片背景不透明度的下限,避免完全透明导致文字不可读 */
private const val MIN_CONTAINER_ALPHA = 0.05f

/**
 * 卡片上大节区间的结构化文本片段(纯 Kotlin,便于单元测试)。
 * [single] 为 true 表示单一大节,否则为跨大节区间。
 */
internal data class SlotRange(
    val single: Boolean,
    val startName: String,
    val endName: String?,
    val startTime: String,
    val endTime: String,
)

/**
 * 根据开始/结束大节序号在时间段列表中解析区间;
 * 任一序号在 [slots] 中不存在时返回 null。
 */
internal fun slotRangeOf(startSlot: Int, endSlot: Int, slots: List<TimeSlot>): SlotRange? {
    val start = slots.firstOrNull { it.slotNumber == startSlot } ?: return null
    val end = slots.firstOrNull { it.slotNumber == endSlot } ?: return null
    return SlotRange(
        single = startSlot == endSlot,
        startName = start.displayName,
        endName = end.displayName,
        startTime = start.startTime,
        endTime = end.endTime,
    )
}

/** 大节区间的人类可读文本:单节为"第一大节 08:30–09:50",跨节为"第一至第二大节 08:30–12:15" */
@Composable
private fun slotRangeText(range: SlotRange): String =
    if (range.single) {
        stringResource(
            R.string.course_card_single_slot_format,
            range.startName,
            range.startTime,
            range.endTime,
        )
    } else {
        stringResource(
            R.string.course_card_range_slot_format,
            range.startName.removeSuffix(SLOT_NAME_SUFFIX),
            range.endName.orEmpty(),
            range.startTime,
            range.endTime,
        )
    }

/**
 * 可复用课程卡片:名称、教师、大节与实际时间、地点、折叠备注与备注按钮。
 * 不直接访问数据库:课程、上课安排与作息时间段均由上层传入,
 * 备注编辑动作经 [onRemarkClick] 交由上层 ViewModel 处理。
 *
 * [compact] 为 true 时使用紧凑排版(适用于周课表网格的小单元格):
 * 备注预览省略(空间受限),但备注按钮始终保留。
 *
 * 外观参数(由上游把用户设置传入,本组件不读取设置):
 * - [showTeacher]/[showLocation]/[showTime] 控制对应信息的显隐(详情页固定全显);
 * - [cornerRadius] 卡片圆角;[containerAlpha] 背景不透明度(0..1,自动钳到下限);
 * - [remarkLines] 备注预览最大行数(1 或 2)。
 */
@Composable
fun CourseCard(
    course: Course,
    occurrence: CourseOccurrence?,
    timeSlots: List<TimeSlot>,
    onRemarkClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showTeacher: Boolean = true,
    showLocation: Boolean = true,
    showTime: Boolean = true,
    cornerRadius: Dp = 12.dp,
    containerAlpha: Float = 0.18f,
    remarkLines: Int = 2,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = Color(course.colorArgb)
                .copy(alpha = containerAlpha.coerceIn(MIN_CONTAINER_ALPHA, 1f)),
        ),
    ) {
        if (compact) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val extra = listOfNotNull(
                    course.teacher.takeIf { showTeacher && it.isNotBlank() },
                    course.location.takeIf { showLocation && it.isNotBlank() },
                ).joinToString(" · ")
                if (extra.isNotBlank()) {
                    Text(
                        text = extra,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showTime) {
                    occurrence
                        ?.let { slotRangeOf(it.startSlot, it.endSlot, timeSlots) }
                        ?.let { range ->
                            Text(
                                text = slotRangeText(range),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                }
                TextButton(
                    onClick = onRemarkClick,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 26.dp, minWidth = 0.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (course.remark.isBlank()) R.string.course_card_remark_add
                            else R.string.course_card_remark_show
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showTeacher && course.teacher.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.course_card_teacher_format, course.teacher),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (showTime) {
                    occurrence
                        ?.let { slotRangeOf(it.startSlot, it.endSlot, timeSlots) }
                        ?.let { range ->
                            Text(
                                text = slotRangeText(range),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                }
                if (showLocation && course.location.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.course_card_location_format, course.location),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (course.remark.isNotBlank()) {
                    Text(
                        text = course.remark,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = remarkLines.coerceIn(1, 2),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onRemarkClick) {
                    Text(
                        stringResource(
                            if (course.remark.isBlank()) R.string.course_card_remark_add
                            else R.string.course_card_remark_show
                        )
                    )
                }
            }
        }
    }
}

// ---------- 预览:覆盖无教师/无地点/无备注、长备注、跨大节与紧凑(网格)场景 ----------

private val previewSlots = listOf(
    TimeSlot(schemeId = 1L, slotNumber = 1, displayName = "第一大节", startTime = "08:30", endTime = "09:50"),
    TimeSlot(schemeId = 1L, slotNumber = 2, displayName = "第二大节", startTime = "10:10", endTime = "12:15"),
)

private fun previewCourse(
    name: String = "高等数学",
    teacher: String = "",
    location: String = "",
    remark: String = "",
) = Course(courseTableId = 1L, name = name, teacher = teacher, location = location, remark = remark)

@Preview(showBackground = true, name = "无教师无地点无备注")
@Composable
private fun CourseCardMinimalPreview() {
    CourseCard(
        course = previewCourse(name = "高等数学"),
        occurrence = null,
        timeSlots = previewSlots,
        onRemarkClick = {},
    )
}

@Preview(showBackground = true, name = "完整信息")
@Composable
private fun CourseCardFullPreview() {
    CourseCard(
        course = previewCourse(
            name = "高等数学",
            teacher = "张老师",
            location = "教学楼 A101",
            remark = "记得带教材",
        ),
        occurrence = CourseOccurrence(courseId = 1L, weekday = 1, startSlot = 1, endSlot = 1),
        timeSlots = previewSlots,
        onRemarkClick = {},
    )
}

@Preview(showBackground = true, name = "跨大节")
@Composable
private fun CourseCardCrossSlotPreview() {
    CourseCard(
        course = previewCourse(name = "大学物理", teacher = "李老师", location = "实验楼 B203"),
        occurrence = CourseOccurrence(courseId = 1L, weekday = 3, startSlot = 1, endSlot = 2),
        timeSlots = previewSlots,
        onRemarkClick = {},
    )
}

@Preview(showBackground = true, name = "长备注")
@Composable
private fun CourseCardLongRemarkPreview() {
    CourseCard(
        course = previewCourse(
            name = "线性代数",
            teacher = "王老师",
            location = "教学楼 C305",
            remark = "这是一段很长的备注,用来验证备注默认折叠、最多显示两行并在超出时显示省略号。这门课需要提前预习第一章到第三章,并完成课后习题中的奇数题。",
        ),
        occurrence = CourseOccurrence(courseId = 1L, weekday = 5, startSlot = 1, endSlot = 2),
        timeSlots = previewSlots,
        onRemarkClick = {},
    )
}

@Preview(showBackground = true, name = "紧凑模式(网格)")
@Composable
private fun CourseCardCompactPreview() {
    CourseCard(
        course = previewCourse(name = "大学物理", teacher = "李老师", location = "实验楼 B203"),
        occurrence = CourseOccurrence(courseId = 1L, weekday = 3, startSlot = 1, endSlot = 2),
        timeSlots = previewSlots,
        onRemarkClick = {},
        compact = true,
    )
}