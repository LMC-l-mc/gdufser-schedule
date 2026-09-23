package com.gdufs.schedule.data.repository

import androidx.room.withTransaction
import com.gdufs.schedule.data.db.AppDatabase
import com.gdufs.schedule.data.db.CourseDao
import com.gdufs.schedule.data.db.CourseOccurrenceDao
import com.gdufs.schedule.data.db.CourseTableDao
import com.gdufs.schedule.data.model.CourseTable
import kotlinx.coroutines.flow.Flow

/**
 * 课程表数据唯一入口。
 * 所有数据库操作必须经过 Repository,禁止 UI 直接访问 DAO。
 * 后续迁移 KMP 时,本接口将上移为共享层接口,Android 实现保留在 data 包。
 */
interface CourseTableRepository {

    fun observeCourseTables(): Flow<List<CourseTable>>

    fun observeCourseTable(id: Long): Flow<CourseTable?>

    /** 新建课表,返回新表 id */
    suspend fun createCourseTable(
        name: String,
        termStartDate: Long,
        totalWeeks: Int,
        firstDayOfWeek: Int,
        timeSlotSchemeId: Long,
    ): Long

    suspend fun updateCourseTable(courseTable: CourseTable)

    /** 删除课程表,由外键级联删除其下课程与上课安排 */
    suspend fun deleteCourseTable(courseTable: CourseTable)

    /**
     * 复制课表:深拷贝元数据、全部课程与上课安排。
     * [newName] 为副本名称(可由 UI 拼接本地化后缀后传入)。
     * 返回新表 id。
     */
    suspend fun duplicateCourseTable(courseTableId: Long, newName: String): Long

    /** 课表下课程数量,用于删除前的提示 */
    suspend fun courseCount(courseTableId: Long): Int
}

class DefaultCourseTableRepository(
    private val database: AppDatabase,
    private val courseTableDao: CourseTableDao,
    private val courseDao: CourseDao,
    private val occurrenceDao: CourseOccurrenceDao,
) : CourseTableRepository {

    override fun observeCourseTables(): Flow<List<CourseTable>> = courseTableDao.observeAll()

    override fun observeCourseTable(id: Long): Flow<CourseTable?> = courseTableDao.observeById(id)

    override suspend fun createCourseTable(
        name: String,
        termStartDate: Long,
        totalWeeks: Int,
        firstDayOfWeek: Int,
        timeSlotSchemeId: Long,
    ): Long {
        val now = System.currentTimeMillis()
        return courseTableDao.insert(
            CourseTable(
                name = name,
                termStartDate = termStartDate,
                totalWeeks = totalWeeks,
                firstDayOfWeek = firstDayOfWeek,
                timeSlotSchemeId = timeSlotSchemeId,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    override suspend fun updateCourseTable(courseTable: CourseTable) {
        courseTableDao.update(courseTable.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteCourseTable(courseTable: CourseTable) {
        courseTableDao.delete(courseTable)
    }

    override suspend fun duplicateCourseTable(courseTableId: Long, newName: String): Long =
        database.withTransaction {
            val source = requireNotNull(courseTableDao.getByIdOnce(courseTableId)) {
                "要复制的课表不存在: id=$courseTableId"
            }
            val now = System.currentTimeMillis()
            val newId = courseTableDao.insert(
                source.copy(id = 0L, name = newName, createdAt = now, updatedAt = now)
            )
            courseDao.getByTableOnce(courseTableId).forEach { courseWithOccurrences ->
                val newCourseId = courseDao.insert(
                    courseWithOccurrences.course.copy(
                        id = 0L,
                        courseTableId = newId,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                occurrenceDao.insertAll(
                    courseWithOccurrences.occurrences.map { it.copy(id = 0L, courseId = newCourseId) }
                )
            }
            newId
        }

    override suspend fun courseCount(courseTableId: Long): Int =
        courseDao.countByTable(courseTableId)
}