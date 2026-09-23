package com.gdufs.schedule.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gdufs.schedule.data.model.TimeSlot
import kotlinx.coroutines.flow.Flow

/**
 * 时间段数据访问对象。
 * UI 层不得直接使用,所有数据库操作必须经由 TimeSlotRepository。
 */
@Dao
interface TimeSlotDao {

    /** 按大节序号升序返回方案内全部时间段 */
    @Query("SELECT * FROM time_slots WHERE schemeId = :schemeId ORDER BY slotNumber")
    fun observeByScheme(schemeId: Long): Flow<List<TimeSlot>>

    /** 一次性读取方案内时间段(按大节序号升序),用于复制方案与重排 */
    @Query("SELECT * FROM time_slots WHERE schemeId = :schemeId ORDER BY slotNumber")
    suspend fun getBySchemeOnce(schemeId: Long): List<TimeSlot>

    @Insert
    suspend fun insertAll(slots: List<TimeSlot>)

    @Insert
    suspend fun insert(slot: TimeSlot): Long

    @Update
    suspend fun update(slot: TimeSlot)

    @Update
    suspend fun updateAll(slots: List<TimeSlot>)

    @Delete
    suspend fun delete(slot: TimeSlot)

    // ---------- 备份与恢复支持 ----------

    @Query("SELECT * FROM time_slots")
    suspend fun getAllOnce(): List<TimeSlot>

    /** 保留原 id 的批量插入,用于"覆盖导入" */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(slots: List<TimeSlot>)

    @Query("DELETE FROM time_slots")
    suspend fun clearAll()
}