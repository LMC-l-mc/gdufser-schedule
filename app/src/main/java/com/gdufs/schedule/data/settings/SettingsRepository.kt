package com.gdufs.schedule.data.settings

import com.gdufs.schedule.data.db.AppSettingsDao
import com.gdufs.schedule.data.model.AppSettings
import com.gdufs.schedule.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 应用设置唯一入口。
 * 阶段 2 起设置持久化于 Room 单行表(app_settings),阶段 1 的 DataStore 骨架数据不再使用;
 * DataStore 依赖保留,预留给后续不需要结构化查询的偏好。
 * 所有读写必须经过本 Repository,默认行由 DefaultScheduleInitializer 保证存在。
 */
interface SettingsRepository {

    /** 完整设置快照;首行不存在时返回默认值 */
    val settings: Flow<AppSettings>

    /** 当前课程表 id,未选择时为 null */
    val currentCourseTableId: Flow<Long?>

    suspend fun setCurrentCourseTable(tableId: Long?)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColorEnabled(enabled: Boolean)

    /**
     * 基于当前设置行应用外观变更并写回(原子地"读当前行-变换-覆盖")。
     * 供外观设置页一次修改一个字段;返回写回后的最新设置。
     */
    suspend fun updateAppearance(transform: (AppSettings) -> AppSettings): AppSettings
}

class SettingsRepositoryImpl(
    private val settingsDao: AppSettingsDao,
) : SettingsRepository {

    override val settings: Flow<AppSettings> =
        settingsDao.observe().map { it ?: AppSettings() }

    override val currentCourseTableId: Flow<Long?> =
        settings.map { it.currentCourseTableId }

    override suspend fun setCurrentCourseTable(tableId: Long?) {
        update { it.copy(currentCourseTableId = tableId) }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        update { it.copy(themeMode = mode) }
    }

    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        update { it.copy(dynamicColorEnabled = enabled) }
    }

    override suspend fun updateAppearance(
        transform: (AppSettings) -> AppSettings,
    ): AppSettings {
        val updated = transform(settingsDao.getOnce() ?: AppSettings())
        settingsDao.upsert(updated)
        return updated
    }

    private suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = settingsDao.getOnce() ?: AppSettings()
        settingsDao.upsert(transform(current))
    }
}