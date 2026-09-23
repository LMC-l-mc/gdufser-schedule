package com.gdufs.schedule.data.repository

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.gdufs.schedule.R
import com.gdufs.schedule.data.db.AppDatabase
import com.gdufs.schedule.data.db.WidgetSnapshotDatabase
import com.gdufs.schedule.data.db.WidgetSnapshotEntity
import com.gdufs.schedule.data.model.AppSettings
import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.data.model.CourseWithOccurrences
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.domain.schedule.ScheduleCalculator
import com.gdufs.schedule.domain.widget.WidgetSnapshotBuilder
import com.gdufs.schedule.ui.widget.TodayWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * 小组件快照同步器:监听主库(设置/课表/课程/时间段)的变化,
 * 在应用进程内完成"今日课程"计算后写入独立快照库,并通知系统刷新小组件。
 * 小组件只读取快照库,不运行任何课表计算。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetSnapshotSyncer(
    private val context: Context,
    mainDatabase: AppDatabase,
    private val snapshotDatabase: WidgetSnapshotDatabase,
) {

    private val tableDao = mainDatabase.courseTableDao()
    private val courseDao = mainDatabase.courseDao()
    private val slotDao = mainDatabase.timeSlotDao()
    private val settingsDao = mainDatabase.appSettingsDao()
    private val snapshotDao = snapshotDatabase.widgetSnapshotDao()

    private data class SyncInput(
        val settings: AppSettings?,
        val table: CourseTable?,
        val courses: List<CourseWithOccurrences>,
        val slots: List<TimeSlot>,
    )

    /** 持续监听主库并按需写入快照;应用主进程启动时调用一次 */
    fun start(scope: CoroutineScope) {
        scope.launch {
            val tableFlow = settingsDao.observe().flatMapLatest { settings ->
                val tableId = settings?.currentCourseTableId ?: return@flatMapLatest flowOf<CourseTable?>(null)
                tableDao.observeById(tableId)
            }
            combine(
                settingsDao.observe(),
                tableFlow,
                tableFlow.flatMapLatest { table ->
                    if (table == null) flowOf(emptyList<CourseWithOccurrences>())
                    else courseDao.observeByTable(table.id)
                },
                tableFlow.flatMapLatest { table ->
                    if (table == null) flowOf(emptyList<TimeSlot>())
                    else slotDao.observeByScheme(table.timeSlotSchemeId)
                },
            ) { settings, table, courses, slots ->
                SyncInput(settings, table, courses, slots)
            }.distinctUntilChanged().collect { input ->
                syncNow(
                    settings = input.settings,
                    table = input.table,
                    courses = input.courses,
                    slots = input.slots,
                )
            }
        }
    }

    /** 执行一次同步(可独立单测):基于当前数据计算今日快照并写库;异常时写 ERROR 快照 */
    suspend fun syncNow(
        settings: AppSettings? = null,
        table: CourseTable? = null,
        courses: List<CourseWithOccurrences>? = null,
        slots: List<TimeSlot>? = null,
        now: LocalTime = LocalTime.now(),
        today: LocalDate = LocalDate.now(),
    ) {
        val effectiveSettings = settings ?: settingsDao.getOnce()
        val effectiveTable = table ?: effectiveSettings?.currentCourseTableId?.let { tableDao.getByIdOnce(it) }
        val effectiveCourses = courses ?: effectiveTable?.let { courseDao.getByTableOnce(it.id) } ?: emptyList()
        val effectiveSlots = slots ?: effectiveTable?.let { slotDao.getBySchemeOnce(it.timeSlotSchemeId) } ?: emptyList()

        val snapshot = try {
            val dateEpochMillis = ScheduleCalculator.toEpochMillis(today)
            if (effectiveTable == null) {
                WidgetSnapshotEntity(
                    dateEpochMillis = dateEpochMillis,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    status = WidgetSnapshotEntity.STATUS_EMPTY,
                    payloadJson = "{}",
                    errorMessage = null,
                )
            } else {
                val content = WidgetSnapshotBuilder.build(
                    table = effectiveTable,
                    courses = effectiveCourses,
                    timeSlots = effectiveSlots,
                    dateEpochMillis = dateEpochMillis,
                    now = now,
                )
                WidgetSnapshotEntity(
                    dateEpochMillis = content.dateEpochMillis,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    status = if (content.items.isEmpty()) WidgetSnapshotEntity.STATUS_EMPTY
                    else WidgetSnapshotEntity.STATUS_READY,
                    payloadJson = WidgetSnapshotBuilder.toPayloadJson(content),
                    errorMessage = null,
                )
            }
        } catch (e: Exception) {
            WidgetSnapshotEntity(
                dateEpochMillis = ScheduleCalculator.toEpochMillis(today),
                updatedAtEpochMillis = System.currentTimeMillis(),
                status = WidgetSnapshotEntity.STATUS_ERROR,
                payloadJson = "{}",
                errorMessage = e.message,
            )
        }
        snapshotDao.upsert(snapshot)
        notifyWidgets()
    }

    /** 通知系统刷新所有"今日课程"小组件实例 */
    private fun notifyWidgets() {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            manager.notifyAppWidgetViewDataChanged(ids, R.layout.widget_today)
        }
    }
}