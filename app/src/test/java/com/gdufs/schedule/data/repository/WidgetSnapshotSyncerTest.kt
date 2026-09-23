package com.gdufs.schedule.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gdufs.schedule.data.db.BaseDatabaseTest
import com.gdufs.schedule.data.db.WidgetSnapshotDatabase
import com.gdufs.schedule.data.db.WidgetSnapshotEntity
import com.gdufs.schedule.data.model.AppSettings
import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.domain.widget.WidgetSnapshotBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 小组件快照同步测试(双内存库):
 * 验证主库数据同步到独立快照库,状态为 READY/EMPTY,且快照内容与主库一致。
 */
class WidgetSnapshotSyncerTest : BaseDatabaseTest() {

    private lateinit var snapshotDatabase: WidgetSnapshotDatabase
    private lateinit var syncer: WidgetSnapshotSyncer

    @Before
    fun setUpSnapshot() {
        snapshotDatabase = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            WidgetSnapshotDatabase::class.java,
        ).allowMainThreadQueries().build()
        syncer = WidgetSnapshotSyncer(
            ApplicationProvider.getApplicationContext(),
            database,
            snapshotDatabase,
        )
    }

    @After
    fun tearDownSnapshot() {
        snapshotDatabase.close()
    }

    private val zone: ZoneId = ZoneId.systemDefault()
    private val termStart: Long = LocalDate.of(2025, 9, 1).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `有今日课程_写入READY快照与今日全部课程`() = runBlocking {
        initializer().initialize()
        val scheme = schemeDao.observeAll().first().single()
        val tableId = courseTableDao.insert(
            CourseTable(
                name = "学期",
                termStartDate = termStart,
                totalWeeks = 20,
                firstDayOfWeek = 1,
                timeSlotSchemeId = scheme.id,
                createdAt = 1L,
                updatedAt = 1L,
            )
        )
        val courseId = courseDao.insert(Course(courseTableId = tableId, name = "高数", teacher = "张"))
        occurrenceDao.insertAll(
            listOf(
                CourseOccurrence(
                    courseId = courseId,
                    weekday = LocalDate.of(2025, 9, 8).dayOfWeek.value,
                    startSlot = 1,
                    endSlot = 1,
                ),
            ),
        )
        settingsDao.upsert(AppSettings(currentCourseTableId = tableId))

        syncer.syncNow(
            table = courseTableDao.getByIdOnce(tableId),
            now = LocalTime.of(7, 0),
            today = LocalDate.of(2025, 9, 8),
        )

        val snapshot = snapshotDatabase.widgetSnapshotDao().getOnce()
        assertNotNull(snapshot)
        assertEquals(WidgetSnapshotEntity.STATUS_READY, snapshot!!.status)
        val content = WidgetSnapshotBuilder.fromPayloadJson(snapshot.payloadJson)
        assertEquals(1, content.items.size)
        assertEquals("高数", content.items.single().name)
        assertEquals("张", content.items.single().teacher)
    }

    @Test
    fun `无当前课表_写入EMPTY快照`() = runBlocking {
        syncer.syncNow(
            settings = AppSettings(currentCourseTableId = null),
            table = null,
            today = LocalDate.of(2025, 9, 8),
        )

        val snapshot = snapshotDatabase.widgetSnapshotDao().getOnce()
        assertEquals(WidgetSnapshotEntity.STATUS_EMPTY, snapshot!!.status)
    }

    @Test
    fun `无课程当日_写入EMPTY快照`() = runBlocking {
        initializer().initialize()
        val scheme = schemeDao.observeAll().first().single()
        val tableId = courseTableDao.insert(
            CourseTable(
                name = "空课表",
                termStartDate = termStart,
                totalWeeks = 20,
                firstDayOfWeek = 1,
                timeSlotSchemeId = scheme.id,
                createdAt = 1L,
                updatedAt = 1L,
            )
        )
        settingsDao.upsert(AppSettings(currentCourseTableId = tableId))

        syncer.syncNow(
            table = courseTableDao.getByIdOnce(tableId),
            today = LocalDate.of(2025, 9, 8),
        )

        assertEquals(
            WidgetSnapshotEntity.STATUS_EMPTY,
            snapshotDatabase.widgetSnapshotDao().getOnce()!!.status,
        )
    }
}