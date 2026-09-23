package com.gdufs.schedule.data.repository

import androidx.room.withTransaction
import com.gdufs.schedule.data.db.AppDatabase
import com.gdufs.schedule.data.model.AppSettings
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.domain.backup.BackupData
import com.gdufs.schedule.domain.backup.IcsExporter

/**
 * 备份/恢复与导入的仓库入口。所有数据库读写集中于此,经 [AppDatabase] 事务执行。
 *
 * 安全约定:调用方必须先经 BackupJson.fromJson 完成解析与校验,
 * 校验未通过不会进入本仓库;写库过程在单个事务内,失败自动回滚,
 * 任何错误文件都不会破坏已有数据。
 */
interface BackupRepository {

    /** 导出当前全部数据(课表/作息/时间段/课程/安排/设置) */
    suspend fun exportAll(): BackupData

    /** 覆盖导入:清空现有全部数据后按原 id 写入备份(当前课表指向备份内对应课表) */
    suspend fun importOverwrite(data: BackupData)

    /** 新建导入:保留现有数据,备份内容以新 id 追加为独立副本 */
    suspend fun importAppend(data: BackupData)

    /** 合并导入:保留现有数据追加;名称与时间段完全一致的作息方案复用现有方案 */
    suspend fun importMerge(data: BackupData)

    /** 导出当前课表的 ICS 日历文本;无当前课表时返回 null */
    suspend fun exportCurrentTableIcs(): String?

    /** 清除全部数据(课表/课程/安排/方案/时间段/设置),恢复到初始状态 */
    suspend fun clearAllData()
}

class DefaultBackupRepository(
    private val database: AppDatabase,
) : BackupRepository {

    private val tableDao = database.courseTableDao()
    private val schemeDao = database.timeSlotSchemeDao()
    private val slotDao = database.timeSlotDao()
    private val courseDao = database.courseDao()
    private val occurrenceDao = database.courseOccurrenceDao()
    private val settingsDao = database.appSettingsDao()

    override suspend fun exportAll(): BackupData {
        return database.withTransaction {
            BackupData(
                exportedAtEpochMillis = System.currentTimeMillis(),
                courseTables = tableDao.getAllOnce(),
                schemes = schemeDao.getAllOnce(),
                timeSlots = slotDao.getAllOnce(),
                courses = courseDao.getAllOnce(),
                occurrences = occurrenceDao.getAllOnce(),
                settings = settingsDao.getOnce() ?: AppSettings(),
            )
        }
    }

    override suspend fun exportCurrentTableIcs(): String? {
        val tableId = settingsDao.getOnce()?.currentCourseTableId ?: return null
        val table = tableDao.getByIdOnce(tableId) ?: return null
        val slots = slotDao.getBySchemeOnce(table.timeSlotSchemeId)
        val courses = courseDao.getByTableOnce(table.id)
        return IcsExporter.export(table, courses, slots)
    }

    override suspend fun clearAllData() {
        database.withTransaction {
            occurrenceDao.clearAll()
            courseDao.clearAll()
            tableDao.clearAll()
            slotDao.clearAll()
            schemeDao.clearAll()
            // 设置行重置为默认(当前课表回到未选择)
            settingsDao.upsert(AppSettings())
        }
    }

    override suspend fun importOverwrite(data: BackupData) {
        database.withTransaction {
            // 清空顺序遵循外键约束:先子表后父表
            occurrenceDao.clearAll()
            courseDao.clearAll()
            tableDao.clearAll()
            slotDao.clearAll()
            schemeDao.clearAll()

            schemeDao.insertAllReplace(data.schemes)
            slotDao.insertAllReplace(data.timeSlots)
            tableDao.insertAllReplace(data.courseTables)
            courseDao.insertAllReplace(data.courses)
            occurrenceDao.insertAllReplace(data.occurrences)

            val importedTables = tableDao.getAllOnce()
            val newCurrent = when {
                data.courseTables.isEmpty() -> null
                importedTables.any { it.id == data.settings.currentCourseTableId } ->
                    data.settings.currentCourseTableId
                else -> importedTables.first().id
            }
            settingsDao.upsert(
                data.settings.copy(
                    id = AppSettings.SETTINGS_ROW_ID,
                    currentCourseTableId = newCurrent,
                ),
            )
        }
    }

    override suspend fun importAppend(data: BackupData) {
        database.withTransaction {
            val schemeIdMap = insertSchemesAppend(data, reuseExisting = false)
            insertTablesCoursesOccurrencesAppend(data, schemeIdMap)
        }
        // 新建导入不改动当前课程表与设置
    }

    override suspend fun importMerge(data: BackupData) {
        database.withTransaction {
            val schemeIdMap = insertSchemesAppend(data, reuseExisting = true)
            insertTablesCoursesOccurrencesAppend(data, schemeIdMap)
        }
    }

    /** 追加/合并导入的作息方案写入;返回旧 id → 新 id 映射 */
    private suspend fun insertSchemesAppend(
        data: BackupData,
        reuseExisting: Boolean,
    ): Map<Long, Long> {
        val importSlotsByScheme = data.timeSlots.groupBy { it.schemeId }
        val map = mutableMapOf<Long, Long>()
        data.schemes.forEach { scheme ->
            val importSlots = importSlotsByScheme[scheme.id].orEmpty()
            val importKey = importSlots.map { SlotKey(it) }.toSet()
            val reusedId = if (reuseExisting) {
                schemeDao.getAllOnce().firstOrNull { existing ->
                    existing.name == scheme.name &&
                        importKey == slotDao.getBySchemeOnce(existing.id).map { SlotKey(it) }.toSet()
                }?.id
            } else {
                null
            }
            val id = reusedId ?: schemeDao.insert(scheme.copy(id = 0L)).also { newId ->
                // 新建方案才写入其时间段;复用现有方案时跳过
                slotDao.insertAll(importSlots.map { it.copy(id = 0L, schemeId = newId) })
            }
            map[scheme.id] = id
        }
        return map
    }

    /** 方案时间段集合的比较键:序号/名称/起止时间一致视为相同方案 */
    private data class SlotKey(
        val slotNumber: Int,
        val displayName: String,
        val startTime: String,
        val endTime: String,
    ) {
        constructor(slot: TimeSlot) : this(slot.slotNumber, slot.displayName, slot.startTime, slot.endTime)
    }

    private suspend fun insertTablesCoursesOccurrencesAppend(
        data: BackupData,
        schemeIdMap: Map<Long, Long>,
    ) {
        val tableIdMap = mutableMapOf<Long, Long>()
        data.courseTables.forEach { table ->
            val newId = tableDao.insert(
                table.copy(
                    id = 0L,
                    timeSlotSchemeId = schemeIdMap[table.timeSlotSchemeId]
                        ?: throw IllegalStateException("课表引用的作息方案 id 未映射"),
                ),
            )
            tableIdMap[table.id] = newId
        }
        val courseIdMap = mutableMapOf<Long, Long>()
        data.courses.forEach { course ->
            val newId = courseDao.insert(
                course.copy(
                    id = 0L,
                    courseTableId = tableIdMap[course.courseTableId]
                        ?: throw IllegalStateException("课程引用的课表 id 未映射"),
                ),
            )
            courseIdMap[course.id] = newId
        }
        data.occurrences.forEach { occurrence ->
            occurrenceDao.insertAll(
                listOf(
                    occurrence.copy(
                        id = 0L,
                        courseId = courseIdMap[occurrence.courseId]
                            ?: throw IllegalStateException("上课安排引用的课程 id 未映射"),
                    )
                )
            )
        }
    }
}