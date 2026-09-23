package com.gdufs.schedule.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gdufs.schedule.data.model.CourseOccurrence
import kotlinx.coroutines.flow.Flow

/**
 * 课程上课安排数据访问对象。
 * UI 层不得直接使用,所有数据库操作必须经由 CourseRepository。
 */
@Dao
interface CourseOccurrenceDao {

    @Query("SELECT * FROM course_occurrences WHERE courseId = :courseId ORDER BY weekday, startSlot")
    fun observeByCourse(courseId: Long): Flow<List<CourseOccurrence>>

    @Query(
        "SELECT * FROM course_occurrences " +
            "WHERE courseId IN (SELECT id FROM courses WHERE courseTableId = :courseTableId) " +
            "ORDER BY weekday, startSlot",
    )
    fun observeByTable(courseTableId: Long): Flow<List<CourseOccurrence>>

    @Insert
    suspend fun insertAll(occurrences: List<CourseOccurrence>)

    @Query("DELETE FROM course_occurrences WHERE courseId = :courseId")
    suspend fun deleteByCourse(courseId: Long)

    // ---------- 备份与恢复支持 ----------

    @Query("SELECT * FROM course_occurrences")
    suspend fun getAllOnce(): List<CourseOccurrence>

    /** 保留原 id 的批量插入,用于"覆盖导入" */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(occurrences: List<CourseOccurrence>)

    @Query("DELETE FROM course_occurrences")
    suspend fun clearAll()
}