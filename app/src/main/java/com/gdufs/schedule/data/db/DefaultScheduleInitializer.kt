package com.gdufs.schedule.data.db

import androidx.room.withTransaction
import com.gdufs.schedule.data.model.AppSettings
import com.gdufs.schedule.data.model.TimeSlotScheme

/**
 * 默认数据初始化器,在应用启动时调用,幂等:
 * 1. 若库中不存在任何作息方案,创建"我的作息"并插入默认七个时间段;
 * 2. 若应用设置行不存在,写入默认设置。
 */
class DefaultScheduleInitializer(
    private val database: AppDatabase,
    private val schemeDao: TimeSlotSchemeDao,
    private val slotDao: TimeSlotDao,
    private val settingsDao: AppSettingsDao,
) {

    suspend fun initialize() {
        database.withTransaction {
            if (schemeDao.count() == 0) {
                val schemeId = schemeDao.insert(
                    TimeSlotScheme(name = DefaultSchedule.DEFAULT_SCHEME_NAME),
                )
                slotDao.insertAll(DefaultSchedule.defaultSlotsFor(schemeId))
            }
            if (settingsDao.getOnce() == null) {
                settingsDao.upsert(AppSettings())
            }
        }
    }
}