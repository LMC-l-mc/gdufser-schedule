package com.gdufs.schedule.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gdufs.schedule.data.model.AppSettings
import com.gdufs.schedule.data.model.ThemeMode
import com.gdufs.schedule.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 外观与主题设置 ViewModel:
 * - 暴露完整设置快照(主题模式、动态颜色与课表/卡片外观);
 * - 所有写入经 [SettingsRepository],在 viewModelScope 协程中执行,
 *   写库后 Room Flow 自动回推,界面立即生效。
 */
class AppearanceViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColorEnabled(enabled) }
    }

    /** 外观字段变更(圆角/间距/行高/透明度/显示开关/备注行数) */
    fun updateAppearance(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.updateAppearance(transform) }
    }
}