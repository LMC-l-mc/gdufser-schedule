package com.gdufs.schedule.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gdufs.schedule.data.model.TimeSlotScheme
import kotlinx.coroutines.flow.Flow

/**
 * 作息方案数据访问对象。
 * UI 层不得直接使用,所有数据库操作必须经由 TimeSlotRepository。
 */
@Dao
interface TimeSlotSchemeDao {

    @Query("SELECT * FROM time_slot_schemes ORDER BY id")
    fun observeAll(): Flow<List<TimeSlotScheme>>

    @Query("SELECT * FROM time_slot_schemes WHERE id = :id")
    suspend fun getByIdOnce(id: Long): TimeSlotScheme?

    @Query("SELECT COUNT(*) FROM time_slot_schemes")
    suspend fun count(): Int

    @Insert
    suspend fun insert(scheme: TimeSlotScheme): Long

    @Delete
    suspend fun delete(scheme: TimeSlotScheme)

    // ---------- 备份与恢复支持 ----------

    @Query("SELECT * FROM time_slot_schemes")
    suspend fun getAllOnce(): List<TimeSlotScheme>

    /** 保留原 id 的批量插入,用于"覆盖导入" */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(schemes: List<TimeSlotScheme>)

    @Query("DELETE FROM time_slot_schemes")
    suspend fun clearAll()
}