package com.gdufs.schedule.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdufs.schedule.R
import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.CourseWithOccurrences
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.data.model.WeekMode
import com.gdufs.schedule.domain.schedule.ScheduleQuery
import com.gdufs.schedule.domain.schedule.WeekRule
import com.gdufs.schedule.domain.validation.CourseFormError
import com.gdufs.schedule.domain.validation.CourseFormValidator
import com.gdufs.schedule.domain.validation.OccurrenceDraft
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private fun CourseOccurrence.toDraft(): OccurrenceDraft = OccurrenceDraft(
    weekday = weekday,
    startSlot = startSlot,
    endSlot = endSlot,
    weekMode = weekMode,
    customWeeks = WeekRule.parseCustomWeeks(customWeeksJson),
)

private fun OccurrenceDraft.toEntity(courseId: Long): CourseOccurrence = CourseOccurrence(
    courseId = courseId,
    weekday = weekday,
    startSlot = startSlot,
    endSlot = endSlot,
    weekMode = weekMode,
    customWeeksJson = WeekRule.formatCustomWeeks(customWeeks),
)

/**
 * 课程新增/编辑页(单上课安排表单):
 * 自上而下依次为课程名称、教师、地点、星期、开始大节、结束大节、周次规律、课程颜色、备注。
 * - 名称/教师/地点/备注为文本输入(弹出键盘);
 * - 星期/开始大节/结束大节为只读下拉字段,点击弹出选项(大节显示完整时间文本);
 * - 周次规律同为点击型字段,弹窗内选择每周/单周/双周/自定义(自定义可选多个生效周次);
 * - 保存前经 [CourseFormValidator] 校验,并经 [ScheduleQuery.conflictsWith] 检查冲突,
 *   发现冲突时提示用户,允许确认后继续保存;
 * - 编辑已有课程时回填第一条安排,保存后以当前表单替换为单条安排。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CourseEditScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    courseId: Long?,
    onDone: () -> Unit,
) {
    val viewModel: CourseViewModel = koinViewModel()
    val courses by viewModel.courses.collectAsState()
    val currentTable by viewModel.currentTable.collectAsState()
    val timeSlots by viewModel.timeSlots.collectAsState()
    val scope = rememberCoroutineScope()
    val weekdayNames = stringArrayResource(R.array.weekday_names)
    val weekEvery = stringResource(weekModeLabelRes(WeekMode.EVERY_WEEK))
    val weekOdd = stringResource(weekModeLabelRes(WeekMode.ODD_WEEKS))
    val weekEven = stringResource(weekModeLabelRes(WeekMode.EVEN_WEEKS))
    val weekCustom = stringResource(weekModeLabelRes(WeekMode.CUSTOM))
    val weekModeLabel: (WeekMode) -> String = { mode ->
        when (mode) {
            WeekMode.EVERY_WEEK -> weekEvery
            WeekMode.ODD_WEEKS -> weekOdd
            WeekMode.EVEN_WEEKS -> weekEven
            WeekMode.CUSTOM -> weekCustom
        }
    }

    val existing = courses.firstOrNull { it.course.id == courseId }
    val isEdit = existing != null

    var name by rememberSaveable { mutableStateOf(existing?.course?.name ?: "") }
    var teacher by rememberSaveable { mutableStateOf(existing?.course?.teacher ?: "") }
    var location by rememberSaveable { mutableStateOf(existing?.course?.location ?: "") }
    var remark by rememberSaveable { mutableStateOf(existing?.course?.remark ?: "") }
    var colorArgb by rememberSaveable {
        mutableStateOf(existing?.course?.colorArgb ?: Course.DEFAULT_COLOR_ARGB)
    }

    // 单条上课安排字段:编辑模式回填第一条安排
    var weekday by rememberSaveable { mutableStateOf(existing?.occurrences?.firstOrNull()?.weekday ?: 1) }
    var startSlot by rememberSaveable {
        mutableStateOf(existing?.occurrences?.firstOrNull()?.startSlot ?: (timeSlots.firstOrNull()?.slotNumber ?: 1))
    }
    var endSlot by rememberSaveable {
        mutableStateOf(existing?.occurrences?.firstOrNull()?.endSlot ?: (timeSlots.firstOrNull()?.slotNumber ?: 1))
    }
    var weekMode by rememberSaveable {
        mutableStateOf(existing?.occurrences?.firstOrNull()?.weekMode ?: WeekMode.EVERY_WEEK)
    }
    var customWeeks by rememberSaveable {
        mutableStateOf(existing?.occurrences?.firstOrNull()?.customWeeksJson?.let { WeekRule.parseCustomWeeks(it) } ?: emptySet())
    }
    var occurrenceReady by remember { mutableStateOf(courseId == null) }
    LaunchedEffect(existing?.course?.id) {
        if (!occurrenceReady && existing != null) {
            val first = existing.occurrences.firstOrNull()
            if (first != null) {
                weekday = first.weekday
                startSlot = first.startSlot
                endSlot = first.endSlot
                weekMode = first.weekMode
                customWeeks = WeekRule.parseCustomWeeks(first.customWeeksJson)
            }
            occurrenceReady = true
        }
    }

    var formErrors by remember { mutableStateOf(setOf<CourseFormError>()) }

    // 冲突确认流
    var conflictPairs by remember {
        mutableStateOf<List<Pair<Course, CourseOccurrence>>>(emptyList())
    }
    var pendingCandidate by remember { mutableStateOf<CourseWithOccurrences?>(null) }

    val maxSlot = timeSlots.size

    fun currentDraft(): OccurrenceDraft = OccurrenceDraft(weekday, startSlot, endSlot, weekMode, customWeeks)

    fun buildCandidate(): CourseWithOccurrences {
        val now = System.currentTimeMillis()
        return CourseWithOccurrences(
            course = Course(
                id = existing?.course?.id ?: 0L,
                courseTableId = currentTable!!.id,
                name = name.trim(),
                teacher = teacher.trim(),
                location = location.trim(),
                remark = remark.trim(),
                colorArgb = colorArgb,
                isEnabled = existing?.course?.isEnabled ?: true,
                createdAt = existing?.course?.createdAt ?: now,
                updatedAt = now,
            ),
            occurrences = listOf(currentDraft().toEntity(courseId = existing?.course?.id ?: 0L)),
        )
    }

    fun doSave(candidate: CourseWithOccurrences) {
        val draft = currentDraft()
        scope.launch {
            val targetCourseId = if (isEdit) {
                val updated = existing!!.course.copy(
                    name = candidate.course.name,
                    teacher = candidate.course.teacher,
                    location = candidate.course.location,
                    remark = candidate.course.remark,
                    colorArgb = candidate.course.colorArgb,
                    updatedAt = System.currentTimeMillis(),
                )
                viewModel.updateCourse(updated)
                updated.id
            } else {
                viewModel.addCourse(
                    courseTableId = currentTable!!.id,
                    name = candidate.course.name,
                    teacher = candidate.course.teacher,
                    location = candidate.course.location,
                    remark = candidate.course.remark,
                    colorArgb = candidate.course.colorArgb,
                )
            }
            viewModel.replaceOccurrences(targetCourseId, listOf(draft.toEntity(targetCourseId)))
            onDone()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
            Text(
                text = stringResource(if (isEdit) R.string.course_edit else R.string.course_new),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        if (currentTable == null) {
            Text(
                text = stringResource(R.string.course_no_current_table_hint),
                style = MaterialTheme.typography.bodyLarge,
            )
            return@Column
        }

        // 1. 课程名称
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.course_name)) },
            isError = CourseFormError.NAME_BLANK in formErrors,
            supportingText = if (CourseFormError.NAME_BLANK in formErrors) {
                { Text(stringResource(R.string.error_course_name_blank)) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // 2. 教师
        OutlinedTextField(
            value = teacher,
            onValueChange = { teacher = it },
            label = { Text(stringResource(R.string.course_teacher)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // 3. 地点
        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text(stringResource(R.string.course_location)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // 4. 星期(点击弹出选项)
        WeekdaySelectField(
            value = weekday,
            options = weekdayNames,
            enabled = timeSlots.isNotEmpty(),
            onSelect = { weekday = it },
        )

        // 5. 开始大节(点击弹出选项,完整显示“第一大节（08:30–09:50）”)
        SlotSelectField(
            label = stringResource(R.string.course_start_slot),
            value = startSlot,
            options = timeSlots,
            enabled = timeSlots.isNotEmpty(),
            onSelect = {
                startSlot = it
                if (endSlot < it) endSlot = it
            },
        )

        // 6. 结束大节(仅允许不小于开始大节)
        SlotSelectField(
            label = stringResource(R.string.course_end_slot),
            value = endSlot,
            options = timeSlots.filter { it.slotNumber >= startSlot },
            enabled = timeSlots.isNotEmpty(),
            isError = CourseFormError.SLOT_ORDER_INVALID in formErrors,
            supportingText = if (CourseFormError.SLOT_ORDER_INVALID in formErrors) {
                stringResource(R.string.error_occurrence_slot_order)
            } else {
                null
            },
            onSelect = { endSlot = it },
        )

        // 7. 周次规律(点击弹出与原先一致的交互)
        WeekRuleSelectField(
            weekMode = weekMode,
            customWeeks = customWeeks,
            totalWeeks = currentTable?.totalWeeks ?: 0,
            enabled = timeSlots.isNotEmpty(),
            weekModeLabel = weekModeLabel,
            isError = CourseFormError.CUSTOM_WEEKS_EMPTY in formErrors,
            errorText = if (CourseFormError.CUSTOM_WEEKS_EMPTY in formErrors) {
                stringResource(R.string.error_custom_weeks_empty)
            } else {
                null
            },
            onApply = { mode, weeks ->
                weekMode = mode
                customWeeks = weeks
            },
        )

        // 8. 课程颜色
        Text(
            text = stringResource(R.string.course_color),
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            COURSE_COLOR_OPTIONS.forEach { option ->
                val selected = option == colorArgb
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(option), CircleShape)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape,
                        )
                        .clickable { colorArgb = option },
                )
            }
        }

        // 9. 备注
        OutlinedTextField(
            value = remark,
            onValueChange = { remark = it },
            label = { Text(stringResource(R.string.course_remark)) },
            placeholder = { Text(stringResource(R.string.course_remark_hint)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        if (timeSlots.isEmpty()) {
            Text(
                text = stringResource(R.string.course_no_time_slots_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        HorizontalDivider()

        Button(
            onClick = {
                val draft = currentDraft()
                val errors = CourseFormValidator.validate(name, listOf(draft), maxSlot).toSet()
                formErrors = errors
                if (errors.isNotEmpty()) return@Button

                val candidate = buildCandidate()
                val others = courses.filter { it.course.id != courseId }
                val conflicts = ScheduleQuery.conflictsWith(candidate, others, currentTable!!.totalWeeks)
                if (conflicts.isEmpty()) {
                    doSave(candidate)
                } else {
                    pendingCandidate = candidate
                    val candidateCourse = candidate.course
                    conflictPairs = conflicts.map { conflict ->
                        if (conflict.firstCourse == candidateCourse) {
                            conflict.secondCourse to conflict.secondOccurrence
                        } else {
                            conflict.firstCourse to conflict.firstOccurrence
                        }
                    }
                }
            },
            enabled = timeSlots.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }
    }

    // 冲突确认对话框:提示但仍可继续保存
    if (conflictPairs.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { conflictPairs = emptyList() },
            title = { Text(stringResource(R.string.conflict_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.conflict_dialog_body))
                    conflictPairs.forEach { (otherCourse, otherOccurrence) ->
                        Text(
                            text = stringResource(
                                R.string.conflict_item_format,
                                otherCourse.name,
                                describeOccurrence(
                                    otherOccurrence,
                                    weekdayNames,
                                    timeSlots,
                                    weekModeLabel = weekModeLabel,
                                ),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCandidate?.let { doSave(it) }
                        pendingCandidate = null
                        conflictPairs = emptyList()
                    },
                ) {
                    Text(stringResource(R.string.conflict_save_anyway))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingCandidate = null
                        conflictPairs = emptyList()
                    },
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** 星期下拉选择字段(只读文本框,点击弹出选项) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekdaySelectField(
    value: Int,
    options: Array<String>,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = options.getOrElse(value - 1) { "" },
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.course_weekday)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(index + 1)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** 大节下拉选择字段(只读文本框,选项显示“第一大节（08:30–09:50）”完整文本) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotSelectField(
    label: String,
    value: Int,
    options: List<TimeSlot>,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val valueText = options.firstOrNull { it.slotNumber == value }?.let {
        stringResource(R.string.course_slot_option_format, it.displayName, it.startTime, it.endTime)
    }.orEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = valueText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            isError = isError,
            label = { Text(label) },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { slot ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.course_slot_option_format,
                                slot.displayName,
                                slot.startTime,
                                slot.endTime,
                            )
                        )
                    },
                    onClick = {
                        onSelect(slot.slotNumber)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * 周次规律点击型字段:
 * - 点击字段展开面板,仅"每周"与"自定义周次"两个选项;
 * - 选择"自定义周次"后面板内展示可上下滑动的周次勾选列表(1..[totalWeeks]),
 *   勾选/取消即时生效,可勾选任意数量;
 * - 面板不会因点击外部而收起,只有点击右侧小三角形才可退出。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WeekRuleSelectField(
    weekMode: WeekMode,
    customWeeks: Set<Int>,
    totalWeeks: Int,
    enabled: Boolean,
    weekModeLabel: (WeekMode) -> String,
    isError: Boolean,
    errorText: String?,
    onApply: (WeekMode, Set<Int>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val valueText = if (weekMode == WeekMode.CUSTOM) {
        weekModeLabel(WeekMode.CUSTOM) +
            customWeeks.sorted().joinToString(prefix = "(", postfix = ")", separator = ",")
    } else {
        weekModeLabel(weekMode)
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        // 忽略外部状态回调:展开/收起只由小三角形控制
        onExpandedChange = { },
    ) {
        OutlinedTextField(
            value = valueText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            isError = isError,
            supportingText = errorText?.let { { Text(it) } },
            label = { Text(stringResource(R.string.course_week_mode)) },
            trailingIcon = {
                Box(
                    modifier = Modifier.clickable(enabled = enabled) { expanded = !expanded },
                ) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .clickable(enabled = enabled) { expanded = true },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            // 点击面板外部不关闭;只有点击右侧小三角形才会收起
            onDismissRequest = { },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = weekMode == WeekMode.EVERY_WEEK,
                        onClick = { onApply(WeekMode.EVERY_WEEK, customWeeks) },
                        label = { Text(stringResource(R.string.week_mode_every)) },
                    )
                    FilterChip(
                        selected = weekMode == WeekMode.CUSTOM,
                        onClick = {
                            onApply(
                                WeekMode.CUSTOM,
                                customWeeks.ifEmpty { setOf(1) }.toSet(),
                            )
                        },
                        label = { Text(stringResource(R.string.week_mode_custom)) },
                    )
                }
                if (weekMode == WeekMode.CUSTOM) {
                    Text(
                        text = stringResource(R.string.course_custom_weeks),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    // 可上下滑动的周次列表,点击行即可勾选/取消
                    Column(
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        (1..totalWeeks).forEach { week ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onApply(
                                            WeekMode.CUSTOM,
                                            if (week in customWeeks) {
                                                customWeeks - week
                                            } else {
                                                customWeeks + week
                                            },
                                        )
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = week in customWeeks,
                                    onCheckedChange = { checked ->
                                        onApply(
                                            WeekMode.CUSTOM,
                                            if (checked) customWeeks + week else customWeeks - week,
                                        )
                                    },
                                )
                                Text(
                                    text = stringResource(R.string.course_week_item_format, week),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}