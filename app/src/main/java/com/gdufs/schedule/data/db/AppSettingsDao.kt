package com.gdufs.schedule.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gdufs.schedule.data.model.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * 应用设置数据访问对象(单行表)。
 * UI 层不得直接使用,所有数据库操作必须经由 SettingsRepository。
 */
@Dao
interface AppSettingsDao {

    @Query("SELECT * FROM app_settings WHERE id = :rowId")
    fun observe(rowId: Int = AppSettings.SETTINGS_ROW_ID): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = :rowId")
    suspend fun getOnce(rowId: Int = AppSettings.SETTINGS_ROW_ID): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: AppSettings)
}