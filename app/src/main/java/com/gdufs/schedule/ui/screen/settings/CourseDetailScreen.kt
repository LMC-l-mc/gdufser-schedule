package com.gdufs.schedule.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdufs.schedule.R
import com.gdufs.schedule.data.model.WeekMode
import com.gdufs.schedule.ui.component.CourseCard
import com.gdufs.schedule.ui.component.CourseRemarkEditor
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 课程详情页:以 [CourseCard] 预览课程(颜色/名称/教师/大节时间/地点/折叠备注),
 * 备注经 [CourseRemarkEditor] 编辑;列出全部上课安排,提供编辑、复制与删除。
 */
@Composable
fun CourseDetailScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    courseId: Long,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
) {
    val viewModel: CourseViewModel = koinViewModel()
    val courses by viewModel.courses.collectAsState()
    val timeSlots by viewModel.timeSlots.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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

    val courseWithOccurrences = courses.firstOrNull { it.course.id == courseId }

    var menuExpanded by remember { mutableStateOf(false) }
    var showRemarkEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val course = courseWithOccurrences?.course
    if (course == null) {
        // 课程数据尚未就绪(或已被删除),展示空态并停留
        Box(modifier = Modifier.fillMaxSize()) {}
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.course_detail),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
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
                        val suffix = context.getString(R.string.course_copy_suffix)
                        scope.launch {
                            viewModel.duplicateCourse(course.id, course.name + suffix)
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.course_delete)) },
                    onClick = {
                        menuExpanded = false
                        deleting = true
                    },
                )
            }
        }

        // 课程卡片:含折叠备注与备注编辑入口
        CourseCard(
            course = course,
            occurrence = courseWithOccurrences.occurrences.firstOrNull(),
            timeSlots = timeSlots,
            onRemarkClick = { showRemarkEditor = true },
        )

        // 上课安排
        Text(
            text = stringResource(R.string.course_occurrences),
            style = MaterialTheme.typography.titleMedium,
        )
        if (courseWithOccurrences.occurrences.isEmpty()) {
            Text(
                text = stringResource(R.string.course_no_occurrence_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            courseWithOccurrences.occurrences.forEach { occurrence ->
                Text(
                    text = describeOccurrence(
                        occurrence,
                        weekdayNames,
                        timeSlots,
                        weekModeLabel = weekModeLabel,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.course_delete_title)) },
            text = { Text(stringResource(R.string.course_delete_message, course.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCourse(course)
                        deleting = false
                        onDeleted()
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showRemarkEditor) {
        CourseRemarkEditor(
            course = course,
            initialRemark = course.remark,
            onDismiss = { showRemarkEditor = false },
            onSave = { remark ->
                viewModel.updateCourseRemark(course, remark)
                showRemarkEditor = false
            },
        )
    }
}