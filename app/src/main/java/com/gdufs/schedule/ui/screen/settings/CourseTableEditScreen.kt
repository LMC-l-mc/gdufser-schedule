package com.gdufs.schedule.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gdufs.schedule.R
import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.domain.schedule.ScheduleCalculator
import com.gdufs.schedule.domain.validation.CourseTableFormError
import com.gdufs.schedule.domain.validation.CourseTableFormValidator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.format.DateTimeFormatter

/** 未选择作息方案时的占位值 */
private const val NO_SCHEME_ID = -1L

/**
 * 课表新建/编辑页:名称、学期开始日期(yyyy-MM-dd)、总周数、
 * 每周第一天与作息方案;保存前经 [CourseTableFormValidator] 校验。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseTableEditScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    tableId: Long?,
    onDone: () -> Unit,
) {
    val viewModel: CourseTableViewModel = koinViewModel()
    val tables by viewModel.courseTables.collectAsState()
    val schemes by viewModel.schemes.collectAsState()
    val scope = rememberCoroutineScope()

    val existing = tables.firstOrNull { it.id == tableId }
    val isEdit = existing != null

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var dateText by rememberSaveable {
        mutableStateOf(existing?.let { formatDate(it.termStartDate) } ?: "")
    }
    var weeksText by rememberSaveable {
        mutableStateOf(existing?.totalWeeks?.toString() ?: "")
    }
    var firstDayOfWeek by rememberSaveable { mutableStateOf(existing?.firstDayOfWeek ?: 1) }
    var schemeId by rememberSaveable { mutableStateOf(existing?.timeSlotSchemeId ?: NO_SCHEME_ID) }

    var errors by remember { mutableStateOf(setOf<CourseTableFormError>()) }
    var showFirstDayMenu by remember { mutableStateOf(false) }
    var showSchemeMenu by remember { mutableStateOf(false) }

    val weekdayNames = stringArrayResource(R.array.weekday_names)

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
                text = stringResource(if (isEdit) R.string.course_table_edit else R.string.course_table_new),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.course_table_name)) },
            isError = CourseTableFormError.NAME_BLANK in errors,
            supportingText = {
                errors.messageFor(
                    CourseTableFormError.NAME_BLANK to R.string.error_course_table_name_blank,
                )?.let { Text(it) }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            label = { Text(stringResource(R.string.course_table_term_start_date)) },
            placeholder = { Text(stringResource(R.string.course_table_date_hint)) },
            isError = CourseTableFormError.DATE_BLANK in errors ||
                CourseTableFormError.DATE_INVALID_FORMAT in errors ||
                CourseTableFormError.DATE_INVALID_VALUE in errors,
            supportingText = {
                errors.messageFor(
                    CourseTableFormError.DATE_BLANK to R.string.error_course_table_date_blank,
                    CourseTableFormError.DATE_INVALID_FORMAT to R.string.error_course_table_date_format,
                    CourseTableFormError.DATE_INVALID_VALUE to R.string.error_course_table_date_invalid,
                )?.let { Text(it) }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = weeksText,
            onValueChange = { weeksText = it },
            label = { Text(stringResource(R.string.course_table_total_weeks)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = CourseTableFormError.TOTAL_WEEKS_BLANK in errors ||
                CourseTableFormError.TOTAL_WEEKS_INVALID in errors ||
                CourseTableFormError.TOTAL_WEEKS_OUT_OF_RANGE in errors,
            supportingText = {
                errors.messageFor(
                    CourseTableFormError.TOTAL_WEEKS_BLANK to R.string.error_course_table_weeks_blank,
                    CourseTableFormError.TOTAL_WEEKS_INVALID to R.string.error_course_table_weeks_invalid,
                    CourseTableFormError.TOTAL_WEEKS_OUT_OF_RANGE to R.string.error_course_table_weeks_range,
                )?.let { Text(it) }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        ExposedDropdownMenuBox(
            expanded = showFirstDayMenu,
            onExpandedChange = { showFirstDayMenu = it },
        ) {
            OutlinedTextField(
                value = weekdayNames.getOrElse(firstDayOfWeek - 1) { "" },
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.course_table_first_day)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showFirstDayMenu) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = showFirstDayMenu,
                onDismissRequest = { showFirstDayMenu = false },
            ) {
                weekdayNames.forEachIndexed { index, weekday ->
                    DropdownMenuItem(
                        text = { Text(weekday) },
                        onClick = {
                            firstDayOfWeek = index + 1
                            showFirstDayMenu = false
                        },
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = showSchemeMenu,
            onExpandedChange = { showSchemeMenu = it },
        ) {
            OutlinedTextField(
                value = schemes.firstOrNull { it.id == schemeId }?.name.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.course_table_scheme)) },
                isError = CourseTableFormError.SCHEME_NOT_SELECTED in errors,
                supportingText = {
                    errors.messageFor(
                        CourseTableFormError.SCHEME_NOT_SELECTED to R.string.error_course_table_scheme_not_selected,
                    )?.let { Text(it) } ?: if (schemes.isEmpty()) {
                        Text(stringResource(R.string.course_table_no_scheme_hint))
                    } else {
                        null
                    }
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSchemeMenu) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = showSchemeMenu,
                onDismissRequest = { showSchemeMenu = false },
            ) {
                if (schemes.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.course_table_no_scheme_hint)) },
                        onClick = {},
                        enabled = false,
                    )
                } else {
                    schemes.forEach { scheme ->
                        DropdownMenuItem(
                            text = { Text(scheme.name) },
                            onClick = {
                                schemeId = scheme.id
                                showSchemeMenu = false
                            },
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                val validation = CourseTableFormValidator.validate(
                    name = name,
                    dateText = dateText,
                    totalWeeksText = weeksText,
                    firstDayOfWeek = firstDayOfWeek,
                    schemeId = schemeId.takeIf { it != NO_SCHEME_ID },
                )
                errors = validation.toSet()
                if (validation.isEmpty()) {
                    val parsedDate = requireNotNull(CourseTableFormValidator.parseDate(dateText))
                    val epochMillis = ScheduleCalculator.toEpochMillis(parsedDate)
                    val totalWeeks = requireNotNull(CourseTableFormValidator.parseWeeks(weeksText))
                    scope.launch {
                        if (isEdit) {
                            viewModel.updateCourseTable(
                                requireNotNull(existing).copy(
                                    name = name,
                                    termStartDate = epochMillis,
                                    totalWeeks = totalWeeks,
                                    firstDayOfWeek = firstDayOfWeek,
                                    timeSlotSchemeId = schemeId,
                                )
                            )
                        } else {
                            viewModel.createCourseTable(
                                name = name,
                                termStartDate = epochMillis,
                                totalWeeks = totalWeeks,
                                firstDayOfWeek = firstDayOfWeek,
                                timeSlotSchemeId = schemeId,
                            )
                        }
                        onDone()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}

private fun formatDate(epochMillis: Long): String =
    DateTimeFormatter.ISO_LOCAL_DATE.format(ScheduleCalculator.toLocalDate(epochMillis))

/**
 * 按候选顺序返回第一个命中的错误文案;全部未命中时返回 null。
 * 必须在 @Composable 上下文中使用(内部调用 stringResource)。
 */
@Composable
private fun Set<CourseTableFormError>.messageFor(
    vararg candidates: Pair<CourseTableFormError, Int>,
): String? {
    for ((error, messageRes) in candidates) {
        if (error in this) return stringResource(messageRes)
    }
    return null
}