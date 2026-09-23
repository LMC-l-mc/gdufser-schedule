package com.gdufs.schedule.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdufs.schedule.R
import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.WeekMode
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 全局课程管理页:支持按课程名/教师/地点搜索,展示当前课表下全部课程,
 * 提供编辑、复制与删除,右下角新增。
 */
@Composable
fun CourseManageScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEdit: (Long?) -> Unit, // null = 新增
    onOpenDetail: (Long) -> Unit,
) {
    val viewModel: CourseViewModel = koinViewModel()
    val courses by viewModel.courses.collectAsState()
    val currentTable by viewModel.currentTable.collectAsState()
    val timeSlots by viewModel.timeSlots.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var query by rememberSaveable { mutableStateOf("") }
    var deletingCourse by remember { mutableStateOf<Course?>(null) }
    val weekdayNames = stringArrayResource(R.array.weekday_names)
    // 预计算周次规则标签(供非 Composable 上下文使用)
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

    val filtered = courses.filter { course ->
        query.isBlank() ||
            course.course.name.contains(query, ignoreCase = true) ||
            course.course.teacher.contains(query, ignoreCase = true) ||
            course.course.location.contains(query, ignoreCase = true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.course_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                )
            }

            when {
                currentTable == null -> {
                    CenteredHint(text = stringResource(R.string.course_no_current_table_hint))
                }

                filtered.isEmpty() && courses.isEmpty() -> {
                    CenteredHint(text = stringResource(R.string.course_none_hint))
                }
                filtered.isEmpty() -> {
                    CenteredHint(text = stringResource(R.string.course_list_empty_search))
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered, key = { it.course.id }) { courseWithOccurrences ->
                            val summary = courseWithOccurrences.occurrences.firstOrNull()?.let {
                                describeOccurrence(
                                    it,
                                    weekdayNames,
                                    timeSlots,
                                    weekModeLabel = weekModeLabel,
                                )
                            }
                            CourseRow(
                                course = courseWithOccurrences.course,
                                occurrenceSummary = summary,
                                onClick = { onOpenDetail(courseWithOccurrences.course.id) },
                                onEdit = { onEdit(courseWithOccurrences.course.id) },
                                onDuplicate = {
                                    val suffix = context.getString(R.string.course_copy_suffix)
                                    scope.launch {
                                        viewModel.duplicateCourse(
                                            courseWithOccurrences.course.id,
                                            courseWithOccurrences.course.name + suffix,
                                        )
                                    }
                                },
                                onDelete = { deletingCourse = courseWithOccurrences.course },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { onEdit(null) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.course_new))
        }
    }

    deletingCourse?.let { course ->
        AlertDialog(
            onDismissRequest = { deletingCourse = null },
            title = { Text(stringResource(R.string.course_delete_title)) },
            text = { Text(stringResource(R.string.course_delete_message, course.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCourse(course)
                        deletingCourse = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCourse = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CenteredHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CourseRow(
    course: Course,
    occurrenceSummary: String?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(course.name) },
        supportingContent = {
            Text(
                text = listOfNotNull(
                    stringResource(R.string.course_teacher_location_format, course.teacher, course.location)
                        .takeIf { course.teacher.isNotBlank() || course.location.isNotBlank() },
                    occurrenceSummary,
                ).joinToString(" · ")
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(Color(course.colorArgb), CircleShape),
            )
        },
        trailingContent = {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.course_edit)) },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.course_duplicate)) },
                    onClick = {
                        menuExpanded = false
                        onDuplicate()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.course_delete)) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}