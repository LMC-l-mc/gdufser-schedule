package com.gdufs.schedule.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gdufs.schedule.data.model.AppSettings
import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.data.model.TimeSlotScheme

/**
 * 应用数据库。
 * 实体之间通过外键级联:删除课程表 → 删除课程 → 删除上课安排;
 * 删除作息方案 → 删除其时间段,被课程表引用的方案禁止删除(RESTRICT)。
 * 版本 3 起提供正式迁移以保留用户数据;2 之前的开发数据采用破坏性迁移。
 */
@Database(
    entities = [
        CourseTable::class,
        TimeSlotScheme::class,
        TimeSlot::class,
        Course::class,
        CourseOccurrence::class,
        AppSettings::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseTableDao(): CourseTableDao

    abstract fun timeSlotSchemeDao(): TimeSlotSchemeDao

    abstract fun timeSlotDao(): TimeSlotDao

    abstract fun courseDao(): CourseDao

    abstract fun courseOccurrenceDao(): CourseOccurrenceDao

    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        const val DB_NAME = "timetable.db"

        /** 版本 2 → 3 的迁移语句清单(与 AppSettings 外观字段一一对应,供实现与测试共用) */
        internal val MIGRATION_2_3_STATEMENTS = listOf(
            "ALTER TABLE app_settings ADD COLUMN cardCornerRadius REAL NOT NULL DEFAULT 12",
            "ALTER TABLE app_settings ADD COLUMN cardSpacing REAL NOT NULL DEFAULT 4",
            "ALTER TABLE app_settings ADD COLUMN timetableRowHeight REAL NOT NULL DEFAULT 88",
            "ALTER TABLE app_settings ADD COLUMN cardOpacity INTEGER NOT NULL DEFAULT 18",
            "ALTER TABLE app_settings ADD COLUMN showTeacher INTEGER NOT NULL DEFAULT 1",
            "ALTER TABLE app_settings ADD COLUMN showLocation INTEGER NOT NULL DEFAULT 1",
            "ALTER TABLE app_settings ADD COLUMN showTime INTEGER NOT NULL DEFAULT 1",
            "ALTER TABLE app_settings ADD COLUMN remarkPreviewLines INTEGER NOT NULL DEFAULT 2",
        )

        /** 版本 2 → 3:app_settings 增加外观设置列,默认值与实体默认值一致 */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3_STATEMENTS.forEach { db.execSQL(it) }
            }
        }

        /** 版本 3 → 4:既有"我的作息"方案更名为"默认作息" */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE time_slot_schemes SET name = '默认作息' WHERE name = '我的作息'")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}