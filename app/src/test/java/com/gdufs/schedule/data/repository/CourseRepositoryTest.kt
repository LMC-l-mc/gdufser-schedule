package com.gdufs.schedule.data.repository

import com.gdufs.schedule.data.db.BaseDatabaseTest
import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.data.model.TimeSlotScheme
import com.gdufs.schedule.data.model.WeekMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课程 Repository 测试:新增、编辑、安排替换、复制深拷贝、级联删除。
 */
class CourseRepositoryTest : BaseDatabaseTest() {

    private val repository: CourseRepository by lazy {
        DefaultCourseRepository(database, courseDao, occurrenceDao)
    }

    private fun seedTable(): Long = runBlocking {
        val schemeId = schemeDao.insert(TimeSlotScheme(name = "测试作息"))
        courseTableDao.insert(
            CourseTable(
                name = "测试课表",
                termStartDate = 1_739_836_800_000L,
                totalWeeks = 16,
                firstDayOfWeek = 1,
                timeSlotSchemeId = schemeId,
                createdAt = 1L,
                updatedAt = 1L,
            )
        )
    }

    private fun occurrence(
        courseId: Long = 0L,
        weekday: Int,
        start: Int,
        end: Int = start,
        mode: WeekMode = WeekMode.EVERY_WEEK,
        custom: String = "",
    ) = CourseOccurrence(
        courseId = courseId,
        weekday = weekday,
        startSlot = start,
        endSlot = end,
        weekMode = mode,
        customWeeksJson = custom,
    )

    // ---------- 新增与安排替换 ----------

    @Test
    fun `新增课程并替换上课安排可查询`() = runBlocking {
        val tableId = seedTable()
        val courseId = repository.addCourse(
            courseTableId = tableId,
            name = "高等数学",
            teacher = "张三",
            location = "北101",
            remark = "每周有作业",
            colorArgb = 0xFF4C6EF5.toInt(),
        )
        repository.replaceOccurrences(
            courseId,
            listOf(
                occurrence(courseId = courseId, weekday = 1, start = 1, end = 2),
                occurrence(courseId = courseId, weekday = 3, start = 4),
            ),
        )

        val loaded = courseDao.getByIdOnce(courseId)!!
        assertEquals("高等数学", loaded.course.name)
        assertEquals("张三", loaded.course.teacher)
        assertEquals("北101", loaded.course.location)
        assertEquals("每周有作业", loaded.course.remark)
        assertEquals(2, loaded.occurrences.size)

        // 再次替换为覆盖
        repository.replaceOccurrences(
            courseId,
            listOf(occurrence(courseId = courseId, weekday = 5, start = 5, end = 6)),
        )
        val reloaded = courseDao.getByIdOnce(courseId)!!
        assertEquals(1, reloaded.occurrences.size)
        assertEquals(5, reloaded.occurrences.single().weekday)
        assertEquals(5, reloaded.occurrences.single().startSlot)
        assertEquals(6, reloaded.occurrences.single().endSlot)
    }

    // ---------- 编辑 ----------

    @Test
    fun `编辑课程更新字段`() = runBlocking {
        val tableId = seedTable()
        val courseId = repository.addCourse(
            tableId, "旧名称", "旧教师", "旧地点", "旧备注", 0xFF4C6EF5.toInt(),
        )
        val course = courseDao.getByIdOnce(courseId)!!.course
        repository.updateCourse(
            course.copy(name = "新名称", teacher = "新教师", location = "新地点", remark = "新备注")
        )

        val updated = courseDao.getByIdOnce(courseId)!!.course
        assertEquals("新名称", updated.name)
        assertEquals("新教师", updated.teacher)
        assertEquals("新地点", updated.location)
        assertEquals("新备注", updated.remark)
    }

    // ---------- 复制 ----------

    @Test
    fun `复制课程深拷贝字段与全部安排`() = runBlocking {
        val tableId = seedTable()
        val courseId = repository.addCourse(
            tableId, "大学英语", "李四", "南201", "备注内容", 0xFF12B886.toInt(),
        )
        repository.replaceOccurrences(
            courseId,
            listOf(
                occurrence(courseId = courseId, weekday = 1, start = 1, end = 2, mode = WeekMode.ODD_WEEKS),
                occurrence(courseId = courseId, weekday = 2, start = 3, mode = WeekMode.CUSTOM, custom = "[2,4]"),
            ),
        )

        val duplicateId = repository.duplicateCourse(courseId, "大学英语 副本")

        val source = courseDao.getByIdOnce(courseId)!!
        val duplicate = courseDao.getByIdOnce(duplicateId)!!

        assertNotEquals(source.course.id, duplicate.course.id)
        assertEquals("大学英语 副本", duplicate.course.name)
        assertEquals(source.course.teacher, duplicate.course.teacher)
        assertEquals(source.course.location, duplicate.course.location)
        assertEquals(source.course.remark, duplicate.course.remark)
        assertEquals(source.course.colorArgb, duplicate.course.colorArgb)
        assertEquals(source.course.courseTableId, duplicate.course.courseTableId) // 归属原课表

        assertEquals(source.occurrences.size, duplicate.occurrences.size)
        source.occurrences.zip(duplicate.occurrences).forEach { (s, d) ->
            assertNotEquals(s.id, d.id)
            assertEquals(s.weekday, d.weekday)
            assertEquals(s.startSlot, d.startSlot)
            assertEquals(s.endSlot, d.endSlot)
            assertEquals(s.weekMode, d.weekMode)
            assertEquals(s.customWeeksJson, d.customWeeksJson)
            assertEquals(duplicateId, d.courseId)
        }
    }

    @Test
    fun `复制不存在的课程抛出异常`() {
        val result = runBlocking {
            runCatching { repository.duplicateCourse(999L, "不存在") }
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    // ---------- 删除 ----------

    @Test
    fun `删除课程级联删除其上课安排`() = runBlocking {
        val tableId = seedTable()
        val courseId = repository.addCourse(
            tableId, "离散数学", "", "", "", Course.DEFAULT_COLOR_ARGB,
        )
        repository.replaceOccurrences(
            courseId,
            listOf(occurrence(courseId = courseId, weekday = 1, start = 1)),
        )

        repository.deleteCourse(courseDao.getByIdOnce(courseId)!!.course)

        assertEquals(null, courseDao.getByIdOnce(courseId))
    }
}