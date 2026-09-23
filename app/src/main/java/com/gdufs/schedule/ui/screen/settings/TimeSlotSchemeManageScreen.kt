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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 作息方案管理页:展示全部方案(含被课表引用数量),
 * 支持新建、复制与删除;被课表使用中的方案不可删除。
 */
@Composable
fun TimeSlotSchemeManageScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenTimeSlots: (Long, String) -> Unit,
) {
    val viewModel: TimeSlotSchemeViewModel = koinViewModel()
    val usages by viewModel.schemeUsages.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var creatingScheme by remember { mutableStateOf(false) }
    var deletingUsage by remember { mutableStateOf<SchemeUsage?>(null) }
    var deletingSlotCount by remember { mutableStateOf(0) }
    var showInUseHint by remember { mutableStateOf(false) }

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
            if (usages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.time_slot_scheme_none_hint),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(usages, key = { it.scheme.id }) { usage ->
                        SchemeRow(
                            usage = usage,
                            onClick = { onOpenTimeSlots(usage.scheme.id, usage.scheme.name) },
                            onDuplicate = {
                                val suffix = context.getString(R.string.course_table_copy_suffix)
                                scope.launch {
                                    viewModel.duplicateScheme(usage.scheme.id, usage.scheme.name + suffix)
                                }
                            },
                            onDeleteRequest = {
                                if (usage.usedByTables > 0) {
                                    showInUseHint = true
                                } else {
                                    scope.launch {
                                        deletingSlotCount = viewModel.timeSlotCount(usage.scheme.id)
                                        deletingUsage = usage
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }

            FloatingActionButton(
                onClick = { creatingScheme = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.time_slot_scheme_new))
            }
        }
    }

    if (creatingScheme) {
        CreateSchemeDialog(
            onDismiss = { creatingScheme = false },
            onCreate = { name ->
                scope.launch {
                    viewModel.createScheme(name)
                    creatingScheme = false
                }
            },
        )
    }

    deletingUsage?.let { usage ->
        AlertDialog(
            onDismissRequest = { deletingUsage = null },
            title = { Text(stringResource(R.string.time_slot_scheme_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.time_slot_scheme_delete_message,
                        usage.scheme.name,
                        deletingSlotCount,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.deleteScheme(usage.scheme.id)
                            deletingUsage = null
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingUsage = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showInUseHint) {
        AlertDialog(
            onDismissRequest = { showInUseHint = false },
            title = { Text(stringResource(R.string.time_slot_scheme_delete_title)) },
            text = { Text(stringResource(R.string.time_slot_scheme_in_use_delete_hint)) },
            confirmButton = {
                TextButton(onClick = { showInUseHint = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SchemeRow(
    usage: SchemeUsage,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(usage.scheme.name) },
        supportingContent = {
            Text(
                text = if (usage.usedByTables > 0) {
                    stringResource(R.string.time_slot_scheme_in_use_format, usage.usedByTables)
                } else {
                    ""
                }
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
                    text = { Text(stringResource(R.string.time_slot_scheme_duplicate)) },
                    onClick = {
                        menuExpanded = false
                        onDuplicate()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.time_slot_scheme_delete)) },
                    enabled = usage.usedByTables == 0,
                    onClick = {
                        menuExpanded = false
                        onDeleteRequest()
                    },
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun CreateSchemeDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.time_slot_scheme_new)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.time_slot_scheme_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}