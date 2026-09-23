package com.gdufs.schedule.domain.validation

import com.gdufs.schedule.data.model.WeekMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 课程表单校验器测试:名称必填、至少一条安排、星期/大节范围/先后、
 * 自定义周次必选;以及多个错误聚合。
 */
class CourseFormValidatorTest {

    private val validName = "高等数学"
    private val maxSlot = 7

    private fun draft(
        weekday: Int = 1,
        startSlot: Int = 1,
        endSlot: Int = 2,
        weekMode: WeekMode = WeekMode.EVERY_WEEK,
        customWeeks: Set<Int> = emptySet(),
    ) = OccurrenceDraft(weekday, startSlot, endSlot, weekMode, customWeeks)

    private fun validate(
        name: String = validName,
        occurrences: List<OccurrenceDraft> = listOf(draft()),
        maxSlot: Int = this.maxSlot,
    ) = CourseFormValidator.validate(name, occurrences, maxSlot)

    // ---------- 名称 ----------

    @Test
    fun `课程名称必填`() {
        assertEquals(listOf(CourseFormError.NAME_BLANK), validate(name = ""))
        assertEquals(listOf(CourseFormError.NAME_BLANK), validate(name = "   "))
    }

    @Test
    fun `全部合法时校验通过`() {
        assertEquals(emptyList<CourseFormError>(), validate())
    }

    // ---------- 上课安排 ----------

    @Test
    fun `至少需要一条上课安排`() {
        assertEquals(listOf(CourseFormError.NO_OCCURRENCE), validate(occurrences = emptyList()))
    }

    @Test
    fun `多条上课安排合法`() {
        val occurrences = listOf(
            draft(weekday = 1, startSlot = 1, endSlot = 2),
            draft(weekday = 3, startSlot = 4, endSlot = 4),
            draft(weekday = 5, startSlot = 6, endSlot = 7, weekMode = WeekMode.ODD_WEEKS),
        )
        assertEquals(emptyList<CourseFormError>(), validate(occurrences = occurrences))
    }

    // ---------- 星期 ----------

    @Test
    fun `星期必须为1到7`() {
        assertEquals(
            listOf(CourseFormError.WEEKDAY_INVALID),
            validate(occurrences = listOf(draft(weekday = 0))),
        )
        assertEquals(
            listOf(CourseFormError.WEEKDAY_INVALID),
            validate(occurrences = listOf(draft(weekday = 8))),
        )
        // 边界值合法
        assertEquals(emptyList<CourseFormError>(), validate(occurrences = listOf(draft(weekday = 1))))
        assertEquals(emptyList<CourseFormError>(), validate(occurrences = listOf(draft(weekday = 7))))
    }

    // ---------- 大节范围与先后 ----------

    @Test
    fun `大节必须在作息方案范围内`() {
        assertEquals(
            listOf(CourseFormError.SLOT_OUT_OF_RANGE),
            validate(occurrences = listOf(draft(startSlot = 0, endSlot = 2))),
        )
        assertEquals(
            listOf(CourseFormError.SLOT_OUT_OF_RANGE),
            validate(occurrences = listOf(draft(startSlot = 6, endSlot = 8))),
        )
        // 第一至第七大节均为合法边界
        assertEquals(emptyList<CourseFormError>(), validate(occurrences = listOf(draft(startSlot = 1, endSlot = 1))))
        assertEquals(emptyList<CourseFormError>(), validate(occurrences = listOf(draft(startSlot = 7, endSlot = 7))))
    }

    @Test
    fun `开始大节不能晚于结束大节`() {
        assertEquals(
            listOf(CourseFormError.SLOT_ORDER_INVALID),
            validate(occurrences = listOf(draft(startSlot = 4, endSlot = 2))),
        )
        // 单大节课程 start == end 合法
        assertEquals(emptyList<CourseFormError>(), validate(occurrences = listOf(draft(startSlot = 3, endSlot = 3))))
    }

    @Test
    fun `跨多个大节合法`() {
        assertEquals(
            emptyList<CourseFormError>(),
            validate(occurrences = listOf(draft(startSlot = 1, endSlot = 7))),
        )
    }

    // ---------- 自定义周次 ----------

    @Test
    fun `自定义周次模式必须至少选择一个周次`() {
        assertEquals(
            listOf(CourseFormError.CUSTOM_WEEKS_EMPTY),
            validate(
                occurrences = listOf(
                    draft(weekMode = WeekMode.CUSTOM, customWeeks = emptySet()),
                ),
            ),
        )
        assertEquals(
            emptyList<CourseFormError>(),
            validate(
                occurrences = listOf(
                    draft(weekMode = WeekMode.CUSTOM, customWeeks = setOf(1, 3, 5)),
                ),
            ),
        )
    }

    @Test
    fun `每周单周双周无需自定义周次`() {
        assertEquals(
            emptyList<CourseFormError>(),
            validate(occurrences = listOf(draft(weekMode = WeekMode.EVERY_WEEK))),
        )
        assertEquals(
            emptyList<CourseFormError>(),
            validate(occurrences = listOf(draft(weekMode = WeekMode.ODD_WEEKS))),
        )
        assertEquals(
            emptyList<CourseFormError>(),
            validate(occurrences = listOf(draft(weekMode = WeekMode.EVEN_WEEKS))),
        )
    }

    // ---------- 错误聚合 ----------

    @Test
    fun `多个字段非法时聚合全部错误`() {
        val errors = validate(
            name = "",
            occurrences = listOf(
                draft(weekday = 9, startSlot = 8, endSlot = 0, weekMode = WeekMode.CUSTOM),
            ),
        )
        assertEquals(
            listOf(
                CourseFormError.NAME_BLANK,
                CourseFormError.WEEKDAY_INVALID,
                CourseFormError.SLOT_OUT_OF_RANGE,
                CourseFormError.SLOT_ORDER_INVALID,
                CourseFormError.CUSTOM_WEEKS_EMPTY,
            ),
            errors,
        )
    }
}