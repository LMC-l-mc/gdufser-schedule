package com.gdufs.schedule.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 数据库迁移测试:
 * 1) 校验迁移语句:八条 ALTER 均添加在 app_settings 上,且关键列默认值与实体默认值一致;
 * 2) 校验当前 Room schema(v3):app_settings 包含全部迁移列。
 * 两端一致即保证 2→3 升级路径正确。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppDatabaseMigrationTest {

    @Test
    fun migrationStatements_coverAllAppearanceColumnsWithDefaults() {
        val statements = AppDatabase.MIGRATION_2_3_STATEMENTS

        assertEquals(8, statements.size)
        assertTrue(statements.all { it.startsWith("ALTER TABLE app_settings ADD COLUMN") })

        val expectedColumns = mapOf(
            "cardCornerRadius" to "DEFAULT 12",
            "cardSpacing" to "DEFAULT 4",
            "timetableRowHeight" to "DEFAULT 88",
            "cardOpacity" to "DEFAULT 18",
            "showTeacher" to "DEFAULT 1",
            "showLocation" to "DEFAULT 1",
            "showTime" to "DEFAULT 1",
            "remarkPreviewLines" to "DEFAULT 2",
        )
        expectedColumns.forEach { (column, default) ->
            val statement = statements.firstOrNull { it.contains(column) }
            assertTrue("缺少列 $column 的迁移语句", statement != null)
            assertTrue("列 $column 默认值不符: $statement", statement!!.contains(default))
        }
    }

    @Test
    fun roomV3Schema_containsAllAppearanceColumns() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val cursor = database.openHelper.writableDatabase
            .query("PRAGMA table_info(app_settings)")
        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()
        database.close()

        val appearanceColumns = listOf(
            "cardCornerRadius", "cardSpacing", "timetableRowHeight", "cardOpacity",
            "showTeacher", "showLocation", "showTime", "remarkPreviewLines",
        )
        appearanceColumns.forEach { column ->
            assertTrue("v3 schema 缺少列 $column", column in columns)
        }
    }
}