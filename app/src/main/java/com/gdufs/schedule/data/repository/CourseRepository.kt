package com.gdufs.schedule.data.repository

import androidx.room.withTransaction
import com.gdufs.schedule.data.db.AppDatabase
import com.gdufs.schedule.data.db.CourseDao
import com.gdufs.schedule.data.db.CourseOccurrenceDao
import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.CourseWithOccurrences
import kotlinx.coroutines.flow.Flow

/**
 * 课程数据唯一入口。
 * 所有数据库操作必须经过 Repository,禁止 UI 直接访问 DAO。
 * 后续迁移 KMP 时,本接口将上移为共享层接口,Android 实现保留在 data 包。
 */
interface CourseRepository {

    fun observeCourses(courseTableId: Long): Flow<List<CourseWithOccurrences>>

    fun observeCourse(id: Long): Flow<CourseWithOccurrences?>

    suspend fun addCourse(
        courseTableId: Long,
        name: String,
        teacher: String,
        location: String,
        remark: String,
        colorArgb: Int,
    ): Long

    suspend fun updateCourse(course: Course)

    /** 删除课程,由外键级联删除其上课安排 */
    suspend fun deleteCourse(course: Course)

    /** 整体替换课程的上课安排(事务内先删后插) */
    suspend fun replaceOccurrences(courseId: Long, occurrences: List<CourseOccurrence>)

    /**
     * 复制课程:深拷贝课程字段与全部上课安排(副本归属原课表)。
     * [newName] 为副本名称(可由 UI 拼接本地化后缀后传入)。返回新课程 id。
     */
    suspend fun duplicateCourse(courseId: Long, newName: String): Long
}

class DefaultCourseRepository(
    private val database: AppDatabase,
    private val courseDao: CourseDao,
    private val occurrenceDao: CourseOccurrenceDao,
) : CourseRepository {

    override fun observeCourses(courseTableId: Long): Flow<List<CourseWithOccurrences>> =
        courseDao.observeByTable(courseTableId)

    override fun observeCourse(id: Long): Flow<CourseWithOccurrences?> =
        courseDao.observeById(id)

    override suspend fun addCourse(
        courseTableId: Long,
        name: String,
        teacher: String,
        location: String,
        remark: String,
        colorArgb: Int,
    ): Long = courseDao.insert(
        Course(
            courseTableId = courseTableId,
            name = name,
            teacher = teacher,
            location = location,
            remark = remark,
            colorArgb = colorArgb,
        )
    )

    override suspend fun updateCourse(course: Course) {
        courseDao.update(course.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteCourse(course: Course) {
        courseDao.delete(course)
    }

    override suspend fun replaceOccurrences(
        courseId: Long,
        occurrences: List<CourseOccurrence>,
    ) {
        database.withTransaction {
            occurrenceDao.deleteByCourse(courseId)
            occurrenceDao.insertAll(occurrences)
        }
    }

    override suspend fun duplicateCourse(courseId: Long, newName: String): Long =
        database.withTransaction {
            val source = requireNotNull(courseDao.getByIdOnce(courseId)) {
                "要复制的课程不存在: id=$courseId"
            }
            val now = System.currentTimeMillis()
            val newId = courseDao.insert(
                source.course.copy(id = 0L, name = newName, createdAt = now, updatedAt = now)
            )
            occurrenceDao.insertAll(
                source.occurrences.map { it.copy(id = 0L, courseId = newId) }
            )
            newId
        }
}