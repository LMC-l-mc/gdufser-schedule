package com.gdufs.schedule.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 课表表单校验器测试:名称、日期格式/取值、总周数、每周第一天、方案选择。
 */
class CourseTableFormValidatorTest {

    private val validName = "2025-2026 学年第二学期"
    private val validDate = "2026-02-25"
    private val validWeeks = "16"
    private val validFirstDay = 1 // 1=周一
    private val validSchemeId = 1L

    private fun validate(
        name: String = validName,
        date: String = validDate,
        weeks: String = validWeeks,
        firstDay: Int = validFirstDay,
        schemeId: Long? = validSchemeId,
    ) = CourseTableFormValidator.validate(name, date, weeks, firstDay, schemeId)

    // ---------- 名称 ----------

    @Test
    fun `名称为空或全空白报错`() {
        assertEquals(listOf(CourseTableFormError.NAME_BLANK), validate(name = ""))
        assertEquals(listOf(CourseTableFormError.NAME_BLANK), validate(name = "   "))
    }

    @Test
    fun `全字段合法时校验通过`() {
        assertEquals(emptyList<CourseTableFormError>(), validate())
    }

    // ---------- 学期开始日期 ----------

    @Test
    fun `日期为空报错`() {
        assertEquals(listOf(CourseTableFormError.DATE_BLANK), validate(date = ""))
        assertEquals(listOf(CourseTableFormError.DATE_BLANK), validate(date = "   "))
    }

    @Test
    fun `日期格式非法报错`() {
        assertEquals(listOf(CourseTableFormError.DATE_INVALID_FORMAT), validate(date = "2026/02/25"))
        assertEquals(listOf(CourseTableFormError.DATE_INVALID_FORMAT), validate(date = "25-02-2026"))
        assertEquals(listOf(CourseTableFormError.DATE_INVALID_FORMAT), validate(date = "2026-2-25"))
    }

    @Test
    fun `日期数值非法报错`() {
        assertEquals(listOf(CourseTableFormError.DATE_INVALID_VALUE), validate(date = "2026-13-01")) // 月份越界
        assertEquals(listOf(CourseTableFormError.DATE_INVALID_VALUE), validate(date = "2026-02-30")) // 日越界
        assertEquals(listOf(CourseTableFormError.DATE_INVALID_VALUE), validate(date = "2026-00-10"))
    }

    @Test
    fun `解析合法日期`() {
        assertEquals(LocalDate.of(2026, 2, 25), CourseTableFormValidator.parseDate("2026-02-25"))
        assertNull(CourseTableFormValidator.parseDate("2026-02-30"))
        assertNull(CourseTableFormValidator.parseDate("abc"))
    }

    // ---------- 总周数 ----------

    @Test
    fun `总周数为空或非整数报错`() {
        assertEquals(listOf(CourseTableFormError.TOTAL_WEEKS_BLANK), validate(weeks = ""))
        assertEquals(listOf(CourseTableFormError.TOTAL_WEEKS_INVALID), validate(weeks = "abc"))
        assertEquals(listOf(CourseTableFormError.TOTAL_WEEKS_INVALID), validate(weeks = "16.5"))
    }

    @Test
    fun `总周数超出范围报错`() {
        assertEquals(listOf(CourseTableFormError.TOTAL_WEEKS_OUT_OF_RANGE), validate(weeks = "0"))
        assertEquals(listOf(CourseTableFormError.TOTAL_WEEKS_OUT_OF_RANGE), validate(weeks = "-1"))
        assertEquals(listOf(CourseTableFormError.TOTAL_WEEKS_OUT_OF_RANGE), validate(weeks = "61"))
    }

    @Test
    fun `总周数边界值合法`() {
        assertEquals(emptyList<CourseTableFormError>(), validate(weeks = "1"))
        assertEquals(emptyList<CourseTableFormError>(), validate(weeks = "60"))
    }

    // ---------- 每周第一天 ----------

    @Test
    fun `每周第一天非法报错`() {
        assertEquals(listOf(CourseTableFormError.FIRST_DAY_INVALID), validate(firstDay = 0))
        assertEquals(listOf(CourseTableFormError.FIRST_DAY_INVALID), validate(firstDay = 8))
    }

    @Test
    fun `每周第一天边界值合法`() {
        for (day in 1..7) {
            assertEquals(emptyList<CourseTableFormError>(), validate(firstDay = day))
        }
    }

    // ---------- 作息方案 ----------

    @Test
    fun `未选择作息方案报错`() {
        assertEquals(listOf(CourseTableFormError.SCHEME_NOT_SELECTED), validate(schemeId = null))
        assertEquals(listOf(CourseTableFormError.SCHEME_NOT_SELECTED), validate(schemeId = 0L))
        assertEquals(listOf(CourseTableFormError.SCHEME_NOT_SELECTED), validate(schemeId = -1L))
    }

    // ---------- 错误聚合 ----------

    @Test
    fun `多个字段非法时聚合全部错误`() {
        val errors = validate(name = "", date = "abc", weeks = "0", firstDay = 0, schemeId = null)
        assertEquals(
            listOf(
                CourseTableFormError.NAME_BLANK,
                CourseTableFormError.DATE_INVALID_FORMAT,
                CourseTableFormError.TOTAL_WEEKS_OUT_OF_RANGE,
                CourseTableFormError.FIRST_DAY_INVALID,
                CourseTableFormError.SCHEME_NOT_SELECTED,
            ),
            errors,
        )
    }
}