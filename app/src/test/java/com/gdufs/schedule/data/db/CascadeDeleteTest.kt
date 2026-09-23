package com.gdufs.schedule.data.db

import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.data.model.WeekMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 级联删除测试:
 * 删除课程表 → 级联删除课程与上课安排;
 * 删除课程 → 级联删除其上课安排。
 */
class CascadeDeleteTest : BaseDatabaseTest() {

    private suspend fun seedTable(): CourseTable {
        initializer().initialize()
        val schemeId = schemeDao.observeAll().first().single().id
        val tableId = courseTableDao.insert(
            CourseTable(
                name = "测试学期",
                termStartDate = 0L,
                totalWeeks = 20,
                firstDayOfWeek = 1,
                timeSlotSchemeId = schemeId,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        val courseId = courseDao.insert(
            Course(courseTableId = tableId, name = "高等数学"),
        )
        occurrenceDao.insertAll(
            listOf(
                CourseOccurrence(courseId = courseId, weekday = 1, startSlot = 1, endSlot = 2),
                CourseOccurrence(
                    courseId = courseId,
                    weekday = 3,
                    startSlot = 3,
                    endSlot = 4,
                    weekMode = WeekMode.ODD_WEEKS,
                ),
            ),
        )
        return courseTableDao.observeById(tableId).first()!!
    }

    @Test
    fun deleteCourseTable_cascadesToCoursesAndOccurrences() = runBlocking {
        val table = seedTable()
        assertEquals(1, courseDao.observeByTable(table.id).first().size)
        assertEquals(2, occurrenceDao.observeByTable(table.id).first().size)

        courseTableDao.delete(table)

        assertTrue(courseDao.observeByTable(table.id).first().isEmpty())
        assertTrue(occurrenceDao.observeByTable(table.id).first().isEmpty())
    }

    @Test
    fun deleteCourse_cascadesToItsOccurrences_only() = runBlocking {
        val table = seedTable()
        val courseToDelete = courseDao.observeByTable(table.id).first().single().course

        courseDao.delete(courseToDelete)

        assertTrue(courseDao.observeByTable(table.id).first().isEmpty())
        assertTrue(occurrenceDao.observeByCourse(courseToDelete.id).first().isEmpty())
        assertTrue(occurrenceDao.observeByTable(table.id).first().isEmpty())
    }

    @Test
    fun observeByTable_returnsCourseWithOccurrences() = runBlocking {
        val table = seedTable()

        val courses = courseDao.observeByTable(table.id).first()
        assertEquals(1, courses.size)
        assertEquals("高等数学", courses.single().course.name)
        assertEquals(2, courses.single().occurrences.size)
    }
}