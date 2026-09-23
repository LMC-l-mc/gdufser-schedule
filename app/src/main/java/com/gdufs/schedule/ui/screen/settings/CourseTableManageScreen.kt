package com.gdufs.schedule.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdufs.schedule.R
import com.gdufs.schedule.data.model.CourseTable
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 课表管理页:列表展示全部课表,支持切换当前课表,
 * 以及新建、编辑、复制、删除(删除前提示课程数量)。
 */
@Composable
fun CourseTableManageScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEdit: (Long?) -> Unit,
) {
    val viewModel: CourseTableViewModel = koinViewModel()
    val tables by viewModel.courseTables.collectAsState()
    val currentTableId by viewModel.currentTableId.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var deletingTable by remember { mutableStateOf<CourseTable?>(null) }
    var deletingCourseCount by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (tables.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.course_table_none_hint),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tables, key = { it.id }) { table ->
                        CourseTableRow(
                            table = table,
                            isCurrent = table.id == currentTableId,
                            onSwitch = { scope.launch { viewModel.switchTo(table.id) } },
                            onEdit = { onEdit(table.id) },
                            onDuplicate = {
                                val suffix = context.getString(R.string.course_table_copy_suffix)
                                scope.launch { viewModel.duplicateCourseTable(table.id, table.name + suffix) }
                            },
                            onDelete = {
                                scope.launch {
                                    deletingCourseCount = viewModel.courseCount(table.id)
                                    deletingTable = table
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }

            FloatingActionButton(
                onClick = { onEdit(null) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.course_table_new))
            }
        }
    }

    deletingTable?.let { table ->
        AlertDialog(
            onDismissRequest = { deletingTable = null },
            title = { Text(stringResource(R.string.course_table_delete_title)) },
            text = {
                Text(
                    stringResource(
                        if (deletingCourseCount > 0) {
                            R.string.course_table_delete_with_courses_message
                        } else {
                            R.string.course_table_delete_empty_message
                        },
                        table.name,
                        deletingCourseCount,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCourseTable(table)
                        deletingTable = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTable = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CourseTableRow(
    table: CourseTable,
    isCurrent: Boolean,
    onSwitch: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = table.name, modifier = Modifier.weight(1f, fill = false))
                if (isCurrent) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.course_table_current_badge)) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        supportingContent = {
            Text(stringResource(R.string.course_table_weeks_format, table.totalWeeks))
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
                    text = { Text(stringResource(R.string.cd_course_table_switch)) },
                    onClick = {
                        menuExpanded = false
                        onSwitch()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.course_table_edit)) },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.course_table_duplicate)) },
                    onClick = {
                        menuExpanded = false
                        onDuplicate()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.course_table_delete)) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSwitch),
    )
}