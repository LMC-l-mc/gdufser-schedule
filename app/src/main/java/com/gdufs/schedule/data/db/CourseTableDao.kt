package com.gdufs.schedule.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gdufs.schedule.data.model.CourseTable
import kotlinx.coroutines.flow.Flow

/**
 * 课程表数据访问对象。
 * UI 层不得直接使用,所有数据库操作必须经由 CourseTableRepository。
 */
@Dao
interface CourseTableDao {

    @Query("SELECT * FROM course_tables ORDER BY createdAt")
    fun observeAll(): Flow<List<CourseTable>>

    @Query("SELECT * FROM course_tables WHERE id = :id")
    fun observeById(id: Long): Flow<CourseTable?>

    @Query("SELECT * FROM course_tables WHERE id = :id")
    suspend fun getByIdOnce(id: Long): CourseTable?

    /** 引用某作息方案的课表数量,用于作息方案删除保护 */
    @Query("SELECT COUNT(*) FROM course_tables WHERE timeSlotSchemeId = :schemeId")
    suspend fun countByScheme(schemeId: Long): Int

    @Insert
    suspend fun insert(courseTable: CourseTable): Long

    @Update
    suspend fun update(courseTable: CourseTable)

    /** 删除课程表,由其外键级联删除课程与上课安排 */
    @Delete
    suspend fun delete(courseTable: CourseTable)

    // ---------- 备份与恢复支持 ----------

    @Query("SELECT * FROM course_tables")
    suspend fun getAllOnce(): List<CourseTable>

    /** 保留原 id 的批量插入,用于"覆盖导入" */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(tables: List<CourseTable>)

    @Query("DELETE FROM course_tables")
    suspend fun clearAll()
}