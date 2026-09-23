package com.gdufs.schedule.ui.screen.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gdufs.schedule.R
import com.gdufs.schedule.data.repository.BackupRepository
import com.gdufs.schedule.domain.backup.BackupData
import com.gdufs.schedule.domain.backup.BackupFormatException
import com.gdufs.schedule.domain.backup.BackupJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 备份/恢复/ICS 导出的 ViewModel:
 * - 文件读写通过 SAF(Storage Access Framework)的 [ContentResolver] 在 IO 协程执行;
 * - 导入先经 [BackupJson.fromJson] 解析校验,失败只提示、不写库;
 * - 覆盖导入在仓库层事务内执行,中途失败自动回滚。
 */
class BackupViewModel(
    private val backupRepository: BackupRepository,
) : ViewModel() {

    /** 需要展示给用户的提示(字符串资源 id,可与一条参数组合) */
    data class UserMessage(val resId: Int, val arg: String? = null)

    data class UiState(
        val message: UserMessage? = null,
        /** 已解析并校验通过、等待用户选择导入方式的备份 */
        val pendingImport: BackupData? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    fun dismissPendingImport() = _uiState.update { it.copy(pendingImport = null) }

    /** 导出 JSON 全量备份到指定 uri */
    fun exportJsonBackup(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val message = runCatchingIO {
                withContext(Dispatchers.IO) {
                    val json = BackupJson.toJson(backupRepository.exportAll())
                    resolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("无法写入目标文件")
                }
                UserMessage(R.string.backup_result_success)
            }.recover { UserMessage(R.string.backup_result_failed, it.message) }.getOrThrow()
            _uiState.update { it.copy(message = message) }
        }
    }

    /** 导出当前课表 ICS 到指定 uri;无当前课表时提示引导 */
    fun exportIcs(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val message = runCatchingIO<UserMessage> {
                val ics = withContext(Dispatchers.IO) { backupRepository.exportCurrentTableIcs() }
                    ?: return@runCatchingIO UserMessage(R.string.backup_no_current_table)
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.use { stream ->
                        stream.write(ics.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("无法写入目标文件")
                }
                UserMessage(R.string.backup_result_success)
            }.recover { UserMessage(R.string.backup_result_failed, it.message) }.getOrThrow()
            _uiState.update { it.copy(message = message) }
        }
    }

    /** 读取并校验备份文件;成功则暂存等待用户选择导入方式 */
    fun prepareImport(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val state = runCatchingIO<UiState> {
                val text = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: throw BackupFormatException("无法读取所选文件")
                }
                // 解析与校验完全置于写库之前:失败不会触碰数据库
                val data = BackupJson.fromJson(text)
                UiState(pendingImport = data)
            }.recover {
                when (it) {
                    is BackupFormatException -> UiState(message = UserMessage(R.string.backup_bad_file))
                    else -> UiState(message = UserMessage(R.string.backup_result_failed, it.message))
                }
            }.getOrThrow()
            _uiState.update { it.copy(message = state.message, pendingImport = state.pendingImport) }
        }
    }

    /** 按用户选择的方式执行导入 */
    fun executeImport(overwrite: Boolean, merge: Boolean) {
        val data = _uiState.value.pendingImport ?: return
        viewModelScope.launch {
            val message = runCatchingIO {
                withContext(Dispatchers.IO) {
                    when {
                        overwrite -> backupRepository.importOverwrite(data)
                        merge -> backupRepository.importMerge(data)
                        else -> backupRepository.importAppend(data)
                    }
                }
                UserMessage(R.string.backup_restore_success)
            }.recover { UserMessage(R.string.backup_result_failed, it.message) }.getOrThrow()
            _uiState.update { it.copy(message = message, pendingImport = null) }
        }
    }

    /** 清除全部数据(危险操作,UI 层需先确认) */
    fun clearAllData() {
        viewModelScope.launch {
            val message = runCatchingIO {
                withContext(Dispatchers.IO) { backupRepository.clearAllData() }
                UserMessage(R.string.backup_clear_data_success)
            }.recover { UserMessage(R.string.backup_result_failed, it.message) }.getOrThrow()
            _uiState.update { it.copy(message = message) }
        }
    }

    private inline fun <T> runCatchingIO(block: () -> T): Result<T> =
        runCatching { block() }
}