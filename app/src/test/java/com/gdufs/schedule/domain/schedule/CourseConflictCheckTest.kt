package com.gdufs.schedule.domain.schedule

import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.CourseWithOccurrences
import com.gdufs.schedule.data.model.WeekMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 保存前冲突检查(conflictsWith)与自定义周次序列化测试。
 */
class CourseConflictCheckTest {

    private fun course(id: Long, name: String) =
        Course(courseTableId = 1L, name = name)

    private fun occurrence(
        weekday: Int,
        start: Int,
        end: Int = start,
        mode: WeekMode = WeekMode.EVERY_WEEK,
        custom: String = "",
    ) = CourseOccurrence(
        courseId = 0L,
        weekday = weekday,
        startSlot = start,
        endSlot = end,
        weekMode = mode,
        customWeeksJson = custom,
    )

    private fun withOccurrences(course: Course, occurrences: List<CourseOccurrence>) =
        CourseWithOccurrences(course, occurrences)

    // ---------- conflictsWith ----------

    @Test
    fun `候选课程与现有课程时间冲突时报出`() {
        val candidate = withOccurrences(
            course(id = 0L, name = "新课程"),
            listOf(occurrence(weekday = 1, start = 1, end = 2)),
        )
        val existing = listOf(
            withOccurrences(
                course(id = 1L, name = "高等数学"),
                listOf(occurrence(weekday = 1, start = 2, end = 3)),
            ),
        )

        val conflicts = ScheduleQuery.conflictsWith(candidate, existing, totalWeeks = 16)
        assertEquals(1, conflicts.size)
        assertEquals("新课程", conflicts.first().firstCourse.name)
        assertEquals("高等数学", conflicts.first().secondCourse.name)
    }

    @Test
    fun `候选课程不冲突时返回空`() {
        val candidate = withOccurrences(
            course(id = 0L, name = "新课程"),
            listOf(occurrence(weekday = 1, start = 1, end = 2)),
        )
        val existing = listOf(
            withOccurrences(
                course(id = 1L, name = "高等数学"),
                listOf(occurrence(weekday = 2, start = 1, end = 2)),
            ),
            withOccurrences(
                course(id = 2L, name = "大学英语"),
                listOf(occurrence(weekday = 1, start = 3, end = 4)),
            ),
        )
        assertTrue(ScheduleQuery.conflictsWith(candidate, existing, totalWeeks = 16).isEmpty())
    }

    @Test
    fun `编辑课程时与自身原有安排的冲突被排除在外`() {
        // 编辑场景:existing 中不含该课程自身(调用方已排除),候选课程保留原 id
        val edited = withOccurrences(
            course(id = 7L, name = "高等数学"),
            listOf(occurrence(weekday = 1, start = 1, end = 2)),
        )
        val others = listOf(
            withOccurrences(
                course(id = 1L, name = "大学英语"),
                listOf(occurrence(weekday = 1, start = 3, end = 4)),
            ),
        )
        assertEquals(emptyList<CourseConflict>(), ScheduleQuery.conflictsWith(edited, others, 16))
    }

    @Test
    fun `单周和双周同位不算冲突`() {
        val candidate = withOccurrences(
            course(id = 0L, name = "新课程"),
            listOf(occurrence(weekday = 3, start = 5, mode = WeekMode.ODD_WEEKS)),
        )
        val existing = listOf(
            withOccurrences(
                course(id = 1L, name = "体育"),
                listOf(occurrence(weekday = 3, start = 5, mode = WeekMode.EVEN_WEEKS)),
            ),
        )
        assertTrue(ScheduleQuery.conflictsWith(candidate, existing, 16).isEmpty())
    }

    @Test
    fun `候选课程自身多条安排冲突也会被报出`() {
        val candidate = withOccurrences(
            course(id = 0L, name = "新课程"),
            listOf(
                occurrence(weekday = 2, start = 5, end = 6),
                occurrence(weekday = 2, start = 6, end = 7),
            ),
        )
        val conflicts = ScheduleQuery.conflictsWith(candidate, emptyList(), 16)
        assertEquals(1, conflicts.size)
        assertEquals("新课程", conflicts.first().firstCourse.name)
        assertEquals("新课程", conflicts.first().secondCourse.name)
    }

    @Test
    fun `自定义周次与每周同位冲突在第4周交集`() {
        val candidate = withOccurrences(
            course(id = 0L, name = "实验课"),
            listOf(occurrence(weekday = 4, start = 3, mode = WeekMode.CUSTOM, custom = "[4]")),
        )
        val existing = listOf(
            withOccurrences(
                course(id = 1L, name = "讲座"),
                listOf(occurrence(weekday = 4, start = 3, mode = WeekMode.EVERY_WEEK)),
            ),
        )
        assertEquals(1, ScheduleQuery.conflictsWith(candidate, existing, 16).size)
    }

    // ---------- 自定义周次序列化往返 ----------

    @Test
    fun `自定义周次序列化与解析往返一致`() {
        val weeks = setOf(5, 1, 3)
        val json = WeekRule.formatCustomWeeks(weeks)
        assertEquals("[1,3,5]", json) // 输出已排序
        assertEquals(weeks, WeekRule.parseCustomWeeks(json))
    }

    @Test
    fun `空周次集合序列化为空串`() {
        assertEquals("", WeekRule.formatCustomWeeks(emptySet()))
        assertEquals(setOf(1), WeekRule.parseCustomWeeks(WeekRule.formatCustomWeeks(setOf(1))))
    }
}