package com.gdufs.schedule.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdufs.schedule.R
import org.koin.androidx.compose.koinViewModel

/** 导入方式选项 */
private enum class ImportMode { Append, Merge, Overwrite }

/**
 * 备份与导出页:JSON 全量备份、JSON 恢复(新建/合并/覆盖)与 ICS 日历导出。
 * 全程离线:文件经系统文件选择器读写,不涉及任何网络。
 * 导入先解析校验(错误文件不写库),覆盖导入需二次确认。
 */
@Composable
fun BackupScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val viewModel: BackupViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val resolver = context.contentResolver

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { viewModel.exportJsonBackup(resolver, it) }
    }
    val exportIcsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri ->
        uri?.let { viewModel.exportIcs(resolver, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.prepareImport(resolver, it) }
    }

    var pendingMode by remember { mutableStateOf<ImportMode?>(null) }
    var confirmOverwrite by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            // 字体放大与横屏时保证全部操作可达
            .verticalScroll(rememberScrollState()),
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
                text = stringResource(R.string.settings_backup),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }

        ListItem(
            headlineContent = { Text(stringResource(R.string.backup_export_json)) },
            supportingContent = { Text(stringResource(R.string.backup_export_json_desc)) },
            modifier = Modifier.clickable {
                exportJsonLauncher.launch("timetable_backup.json")
            },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.backup_restore)) },
            supportingContent = { Text(stringResource(R.string.backup_restore_desc)) },
            modifier = Modifier.clickable {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.backup_export_ics)) },
            supportingContent = { Text(stringResource(R.string.backup_export_ics_desc)) },
            modifier = Modifier.clickable {
                exportIcsLauncher.launch("timetable.ics")
            },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.backup_clear_data)) },
            supportingContent = { Text(stringResource(R.string.backup_clear_data_desc)) },
            modifier = Modifier.clickable { confirmClear = true },
        )
    }

    // 清除全部数据二次确认
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.backup_clear_data_confirm_title)) },
            text = { Text(stringResource(R.string.backup_clear_data_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearAllData()
                }) {
                    Text(stringResource(R.string.backup_clear_data))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // 导入方式选择
    state.pendingImport?.let { data ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingImport() },
            title = { Text(stringResource(R.string.backup_choose_mode_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(
                            R.string.backup_import_preview,
                            data.courseTables.size,
                            data.courses.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    ImportMode.entries.forEach { mode ->
                        val selected = pendingMode == mode
                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(
                                        when (mode) {
                                            ImportMode.Append -> R.string.backup_mode_append
                                            ImportMode.Merge -> R.string.backup_mode_merge
                                            ImportMode.Overwrite -> R.string.backup_mode_overwrite
                                        }
                                    )
                                )
                            },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        when (mode) {
                                            ImportMode.Append -> R.string.backup_mode_append_desc
                                            ImportMode.Merge -> R.string.backup_mode_merge_desc
                                            ImportMode.Overwrite -> R.string.backup_mode_overwrite_desc
                                        }
                                    )
                                )
                            },
                            leadingContent = {
                                RadioButton(selected = selected, onClick = { pendingMode = mode })
                            },
                            modifier = Modifier.clickable { pendingMode = mode },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (pendingMode) {
                            ImportMode.Overwrite -> {
                                confirmOverwrite = true
                            }
                            else -> {
                                pendingMode?.let {
                                    viewModel.executeImport(overwrite = false, merge = it == ImportMode.Merge)
                                }
                                pendingMode = null
                            }
                        }
                    },
                    enabled = pendingMode != null,
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingMode = null
                    viewModel.dismissPendingImport()
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // 覆盖导入二次确认
    if (confirmOverwrite) {
        AlertDialog(
            onDismissRequest = { confirmOverwrite = false },
            title = { Text(stringResource(R.string.backup_overwrite_confirm_title)) },
            text = { Text(stringResource(R.string.backup_overwrite_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmOverwrite = false
                    pendingMode = null
                    viewModel.executeImport(overwrite = true, merge = false)
                }) {
                    Text(stringResource(R.string.backup_mode_overwrite))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmOverwrite = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // 操作结果提示
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissMessage() },
            title = null,
            text = {
                Text(
                    text = message.arg?.let { stringResource(message.resId, it) }
                        ?: stringResource(message.resId),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissMessage() }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}