package com.gdufs.schedule.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 小组件快照实体(单行表):主库数据变化时由 [com.gdufs.schedule.data.repository.WidgetSnapshotSyncer]
 * 重新计算并覆盖写入;小组件只读取本表,不运行任何课表计算。
 *
 * [payloadJson] 为结构化 JSON(日期标签/星期序号/今日条目数组/下一节下标),
 * 展示文本由小组件端使用字符串资源拼装。
 */
@Entity(tableName = "widget_snapshot")
data class WidgetSnapshotEntity(
    @PrimaryKey val id: Int = SNAPSHOT_ROW_ID,
    /** 快照对应的日期(当天 0 点 epoch 毫秒),用于判定是否过期 */
    val dateEpochMillis: Long,
    /** 写入时间 */
    val updatedAtEpochMillis: Long,
    /** 状态:READY(有数据)/EMPTY(今日无课程)/ERROR(同步失败,错误信息见 [errorMessage]) */
    val status: String,
    val payloadJson: String,
    val errorMessage: String?,
) {
    companion object {
        const val SNAPSHOT_ROW_ID: Int = 1
        const val STATUS_READY: String = "READY"
        const val STATUS_EMPTY: String = "EMPTY"
        const val STATUS_ERROR: String = "ERROR"
    }
}

@Dao
interface WidgetSnapshotDao {

    @Query("SELECT * FROM widget_snapshot WHERE id = :rowId")
    fun observe(rowId: Int = WidgetSnapshotEntity.SNAPSHOT_ROW_ID): Flow<WidgetSnapshotEntity?>

    @Query("SELECT * FROM widget_snapshot WHERE id = :rowId")
    suspend fun getOnce(rowId: Int = WidgetSnapshotEntity.SNAPSHOT_ROW_ID): WidgetSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: WidgetSnapshotEntity)
}

/**
 * 小组件独立快照数据库(与主库分离,文件为 widget_snapshot.db):
 * 小组件仅经 [WidgetSnapshotDao] 读取,避免依赖主库表结构与复杂查询。
 */
@Database(
    entities = [WidgetSnapshotEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class WidgetSnapshotDatabase : RoomDatabase() {

    abstract fun widgetSnapshotDao(): WidgetSnapshotDao

    companion object {
        const val DB_NAME = "widget_snapshot.db"

        fun build(context: Context): WidgetSnapshotDatabase =
            Room.databaseBuilder(context.applicationContext, WidgetSnapshotDatabase::class.java, DB_NAME)
                .build()
    }
}