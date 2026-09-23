package com.gdufs.schedule.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseWithOccurrences
import kotlinx.coroutines.flow.Flow

/**
 * 课程数据访问对象。
 * UI 层不得直接使用,所有数据库操作必须经由 CourseRepository。
 */
@Dao
interface CourseDao {

    @Transaction
    @Query("SELECT * FROM courses WHERE courseTableId = :courseTableId ORDER BY name")
    fun observeByTable(courseTableId: Long): Flow<List<CourseWithOccurrences>>

    @Transaction
    @Query("SELECT * FROM courses WHERE id = :id")
    fun observeById(id: Long): Flow<CourseWithOccurrences?>

    /** 一次性读取课表下全部课程(含上课安排),用于复制课表 */
    @Transaction
    @Query("SELECT * FROM courses WHERE courseTableId = :courseTableId")
    suspend fun getByTableOnce(courseTableId: Long): List<CourseWithOccurrences>

    /** 一次性读取单门课程(含上课安排),用于复制课程 */
    @Transaction
    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getByIdOnce(id: Long): CourseWithOccurrences?

    /** 课表下课程数量,用于删除课表前的提示 */
    @Query("SELECT COUNT(*) FROM courses WHERE courseTableId = :courseTableId")
    suspend fun countByTable(courseTableId: Long): Int

    @Insert
    suspend fun insert(course: Course): Long

    @Update
    suspend fun update(course: Course)

    /** 删除课程,由其外键级联删除上课安排 */
    @Delete
    suspend fun delete(course: Course)

    // ---------- 备份与恢复支持 ----------

    @Query("SELECT * FROM courses")
    suspend fun getAllOnce(): List<Course>

    /** 保留原 id 的批量插入,用于"覆盖导入" */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(courses: List<Course>)

    @Query("DELETE FROM courses")
    suspend fun clearAll()
}