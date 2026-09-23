package com.gdufs.schedule.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 数据库单元测试基类:每个用例使用独立的内存数据库。
 * 使用基础 Application,避免 ScheduleApplication 的 Koin 初始化重复执行。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
abstract class BaseDatabaseTest {

    protected lateinit var database: AppDatabase
    protected lateinit var schemeDao: TimeSlotSchemeDao
    protected lateinit var slotDao: TimeSlotDao
    protected lateinit var courseTableDao: CourseTableDao
    protected lateinit var courseDao: CourseDao
    protected lateinit var occurrenceDao: CourseOccurrenceDao
    protected lateinit var settingsDao: AppSettingsDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        schemeDao = database.timeSlotSchemeDao()
        slotDao = database.timeSlotDao()
        courseTableDao = database.courseTableDao()
        courseDao = database.courseDao()
        occurrenceDao = database.courseOccurrenceDao()
        settingsDao = database.appSettingsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    protected fun initializer(): DefaultScheduleInitializer =
        DefaultScheduleInitializer(database, schemeDao, slotDao, settingsDao)
}