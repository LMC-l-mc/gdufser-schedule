package com.gdufs.schedule.data.repository

import com.gdufs.schedule.data.db.BaseDatabaseTest
import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.data.model.TimeSlotScheme
import com.gdufs.schedule.data.model.WeekMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表 Repository 测试:创建、编辑、复制(深拷贝课程与安排)、课程计数、删除提示数据。
 */
class CourseTableRepositoryTest : BaseDatabaseTest() {

    private val repository: CourseTableRepository by lazy {
        DefaultCourseTableRepository(database, courseTableDao, courseDao, occurrenceDao)
    }

    private fun seedScheme(): Long = runBlocking {
        schemeDao.insert(TimeSlotScheme(name = "测试作息"))
    }

    private fun seedTable(schemeId: Long = seedScheme(), name: String = "2025-2026 第二学期"): Long =
        runBlocking {
            courseTableDao.insert(
                CourseTable(
                    name = name,
                    termStartDate = 1_739_836_800_000L, // 2026-02-25 00:00 任意固定值
                    totalWeeks = 16,
                    firstDayOfWeek = 1,
                    timeSlotSchemeId = schemeId,
                    createdAt = 1L,
                    updatedAt = 1L,
                )
            )
        }

    private fun seedCourses(tableId: Long): List<Long> = runBlocking {
        val courseId = courseDao.insert(
            Course(courseTableId = tableId, name = "高等数学", teacher = "张三", location = "北101")
        )
        occurrenceDao.insertAll(
            listOf(
                CourseOccurrence(courseId = courseId, weekday = 1, startSlot = 1, endSlot = 2),
                CourseOccurrence(
                    courseId = courseId,
                    weekday = 3,
                    startSlot = 5,
                    endSlot = 5,
                    weekMode = WeekMode.ODD_WEEKS,
                ),
            )
        )
        listOf(courseId)
    }

    @Test
    fun `创建课表返回有效id且可查询`() = runBlocking {
        val schemeId = seedScheme()
        val newId = repository.createCourseTable(
            name = "新学籍课表",
            termStartDate = 1_739_836_800_000L,
            totalWeeks = 18,
            firstDayOfWeek = 7,
            timeSlotSchemeId = schemeId,
        )
        val table = courseTableDao.getByIdOnce(newId)
        assertEquals("新学籍课表", table?.name)
        assertEquals(18, table?.totalWeeks)
        assertEquals(7, table?.firstDayOfWeek)
        assertEquals(schemeId, table?.timeSlotSchemeId)
    }

    @Test
    fun `编辑课表更新字段`() = runBlocking {
        val tableId = seedTable()
        val table = courseTableDao.getByIdOnce(tableId)!!
        repository.updateCourseTable(table.copy(name = "改名后的课表", totalWeeks = 20))
        val updated = courseTableDao.getByIdOnce(tableId)!!
        assertEquals("改名后的课表", updated.name)
        assertEquals(20, updated.totalWeeks)
    }

    @Test
    fun `课程计数反映删除提示所需数量`() = runBlocking {
        val emptyTableId = seedTable(name = "空课表")
        assertEquals(0, repository.courseCount(emptyTableId))

        val tableId = seedTable(name = "有课课表")
        seedCourses(tableId)
        assertEquals(1, repository.courseCount(tableId))
    }

    @Test
    fun `复制课表深拷贝元数据课程与安排且互不影响`() = runBlocking {
        val sourceId = seedTable()
        seedCourses(sourceId)

        val duplicateId = repository.duplicateCourseTable(sourceId, "2025-2026 第二学期 副本")

        // 原表不受影响
        assertEquals(1, courseDao.getByTableOnce(sourceId).size)
        assertEquals(1, courseDao.getByTableOnce(duplicateId).size)

        val source = courseDao.getByTableOnce(sourceId).first()
        val duplicate = courseDao.getByTableOnce(duplicateId).first()

        // 课程内容一致但 id 已变化
        assertNotEquals(source.course.id, duplicate.course.id)
        assertEquals(source.course.name, duplicate.course.name)
        assertEquals(source.course.teacher, duplicate.course.teacher)
        assertEquals(source.course.location, duplicate.course.location)
        assertEquals(duplicateId, duplicate.course.courseTableId)

        // 上课安排完整复制且 id 已变化
        assertEquals(source.occurrences.size, duplicate.occurrences.size)
        source.occurrences.zip(duplicate.occurrences).forEach { (s, d) ->
            assertNotEquals(s.id, d.id)
            assertEquals(s.weekday, d.weekday)
            assertEquals(s.startSlot, d.startSlot)
            assertEquals(s.endSlot, d.endSlot)
            assertEquals(s.weekMode, d.weekMode)
            assertEquals(s.customWeeksJson, d.customWeeksJson)
            assertEquals(duplicate.course.id, d.courseId)
        }

        // 副本名称为传入的新名称
        assertEquals("2025-2026 第二学期 副本", courseTableDao.getByIdOnce(duplicateId)?.name)
    }

    @Test
    fun `复制空课表只复制元数据`() = runBlocking {
        val sourceId = seedTable(name = "空表")
        val duplicateId = repository.duplicateCourseTable(sourceId, "空表 副本")
        assertTrue(courseDao.getByTableOnce(duplicateId).isEmpty())
        assertEquals("空表 副本", courseTableDao.getByIdOnce(duplicateId)?.name)
    }

    @Test
    fun `删除课表级联删除课程并清空课程计数`() = runBlocking {
        val tableId = seedTable()
        seedCourses(tableId)
        assertEquals(1, repository.courseCount(tableId))

        repository.deleteCourseTable(courseTableDao.getByIdOnce(tableId)!!)

        assertTrue(courseDao.getByTableOnce(tableId).isEmpty())
        assertEquals(null, courseTableDao.getByIdOnce(tableId))
    }
}