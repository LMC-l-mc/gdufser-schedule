package com.gdufs.schedule.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdufs.schedule.R
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.domain.validation.TimeSlotError
import com.gdufs.schedule.domain.validation.TimeSlotValidator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** 时间段编辑对话框状态:editing 为 null 表示新增 */
private data class SlotDialogState(val editing: TimeSlot?)

/**
 * 时间段编辑页:展示方案内全部大节,支持添加、编辑、删除与上移/下移排序。
 * 保存前校验开始早于结束、且与相邻时间段不重叠。
 */
@Composable
fun TimeSlotEditScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    schemeId: Long,
    schemeName: String,
) {
    val viewModel: TimeSlotSchemeViewModel = koinViewModel()
    val slots by viewModel.observeTimeSlots(schemeId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var dialogState by remember { mutableStateOf<SlotDialogState?>(null) }
    var deletingSlot by remember { mutableStateOf<TimeSlot?>(null) }

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
                Text(
                    text = schemeName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            if (slots.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.time_slot_empty_hint),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(slots, key = { _, slot -> slot.id }) { index, slot ->
                        SlotRow(
                            slot = slot,
                            canMoveUp = index > 0,
                            canMoveDown = index < slots.lastIndex,
                            onMoveUp = {
                                scope.launch { viewModel.moveTimeSlot(schemeId, index, index - 1) }
                            },
                            onMoveDown = {
                                scope.launch { viewModel.moveTimeSlot(schemeId, index, index + 1) }
                            },
                            onEdit = { dialogState = SlotDialogState(editing = slot) },
                            onDelete = { deletingSlot = slot },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { dialogState = SlotDialogState(editing = null) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.time_slot_add))
        }
    }

    dialogState?.let { state ->
        SlotEditDialog(
            state = state,
            allSlots = slots,
            onDismiss = { dialogState = null },
            onSave = { name, start, end ->
                scope.launch {
                    val editing = state.editing
                    if (editing == null) {
                        viewModel.addTimeSlot(schemeId, name, start, end)
                    } else {
                        viewModel.updateTimeSlot(
                            editing.copy(displayName = name, startTime = start, endTime = end)
                        )
                    }
                    dialogState = null
                }
            },
        )
    }

    deletingSlot?.let { slot ->
        AlertDialog(
            onDismissRequest = { deletingSlot = null },
            title = { Text(stringResource(R.string.time_slot_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.time_slot_delete_message,
                        slot.displayName,
                        slot.startTime,
                        slot.endTime,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.deleteTimeSlot(slot)
                            deletingSlot = null
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSlot = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SlotRow(
    slot: TimeSlot,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = slot.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${slot.startTime}–${slot.endTime}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.cd_move_up))
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_move_down))
        }
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.time_slot_edit)) },
                onClick = {
                    menuExpanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.time_slot_delete)) },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun SlotEditDialog(
    state: SlotDialogState,
    allSlots: List<TimeSlot>,
    onDismiss: () -> Unit,
    onSave: (name: String, startTime: String, endTime: String) -> Unit,
) {
    val editing = state.editing
    var name by remember(state) { mutableStateOf(editing?.displayName ?: "") }
    var startText by remember(state) { mutableStateOf(editing?.startTime ?: "") }
    var endText by remember(state) { mutableStateOf(editing?.endTime ?: "") }
    var rangeErrors by remember(state) { mutableStateOf(emptyList<TimeSlotError>()) }
    var overlappedSlots by remember(state) { mutableStateOf(emptyList<TimeSlot>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (editing == null) R.string.time_slot_add else R.string.time_slot_edit))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.time_slot_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text(stringResource(R.string.time_slot_start)) },
                    placeholder = { Text(stringResource(R.string.time_slot_time_hint)) },
                    isError = TimeSlotError.START_INVALID_FORMAT in rangeErrors,
                    supportingText = if (TimeSlotError.START_INVALID_FORMAT in rangeErrors) {
                        { Text(stringResource(R.string.error_time_slot_start_format)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text(stringResource(R.string.time_slot_end)) },
                    placeholder = { Text(stringResource(R.string.time_slot_time_hint)) },
                    isError = TimeSlotError.END_INVALID_FORMAT in rangeErrors,
                    supportingText = if (TimeSlotError.END_INVALID_FORMAT in rangeErrors) {
                        { Text(stringResource(R.string.error_time_slot_end_format)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (TimeSlotError.START_NOT_BEFORE_END in rangeErrors) {
                    Text(
                        text = stringResource(R.string.error_time_slot_order),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                overlappedSlots.forEach { overlapped ->
                    Text(
                        text = stringResource(R.string.error_time_slot_overlap, overlapped.displayName),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val errors = TimeSlotValidator.validateRange(startText, endText)
                    rangeErrors = errors
                    overlappedSlots = emptyList()
                    if (errors.isEmpty()) {
                        val startMinutes = requireNotNull(TimeSlotValidator.parseMinutes(startText))
                        val endMinutes = requireNotNull(TimeSlotValidator.parseMinutes(endText))
                        overlappedSlots = TimeSlotValidator.findOverlaps(
                            startMinutes = startMinutes,
                            endMinutes = endMinutes,
                            existing = allSlots.filter { it.id != editing?.id },
                        )
                        if (overlappedSlots.isEmpty()) {
                            onSave(name.trim(), startText.trim(), endText.trim())
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}