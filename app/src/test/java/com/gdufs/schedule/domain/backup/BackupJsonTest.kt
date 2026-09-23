package com.gdufs.schedule.domain.backup

import com.gdufs.schedule.data.model.AppSettings
import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.data.model.ThemeMode
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.data.model.TimeSlotScheme
import com.gdufs.schedule.data.model.WeekMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份 JSON 编解码测试:
 * 覆盖含备注/无备注、跨大节、单双周/自定义周次与各种不兼容 JSON(结构缺失、类型错误、
 * 版本不符、引用不一致)。任何不兼容文件都必须抛出 [BackupFormatException],不产生数据。
 */
class BackupJsonTest {

    private fun sampleData(): BackupData = BackupData(
        exportedAtEpochMillis = 1_700_000_000_000L,
        courseTables = listOf(
            CourseTable(
                id = 1L,
                name = "2025 学年第二学期",
                termStartDate = 1_752_571_200_000L,
                totalWeeks = 20,
                firstDayOfWeek = 1,
                timeSlotSchemeId = 1L,
                createdAt = 1_752_570_000_000L,
                updatedAt = 1_752_570_000_000L,
            )
        ),
        schemes = listOf(TimeSlotScheme(id = 1L, name = "我的作息")),
        timeSlots = listOf(
            TimeSlot(id = 1L, schemeId = 1L, slotNumber = 1, displayName = "第一大节", startTime = "08:30", endTime = "09:50"),
            TimeSlot(id = 2L, schemeId = 1L, slotNumber = 2, displayName = "第二大节", startTime = "10:10", endTime = "12:15"),
        ),
        courses = listOf(
            Course(
                id = 1L,
                courseTableId = 1L,
                name = "高等数学",
                teacher = "张老师",
                location = "教学楼 A101",
                remark = "记得带教材\n下周期中考试",
                colorArgb = 0xFF4C6EF5.toInt(),
            ),
            Course(id = 2L, courseTableId = 1L, name = "大学英语"),
        ),
        occurrences = listOf(
            // 跨大节:第一大节～第二大节
            CourseOccurrence(id = 1L, courseId = 1L, weekday = 1, startSlot = 1, endSlot = 2, weekMode = WeekMode.EVERY_WEEK),
            // 单周
            CourseOccurrence(id = 2L, courseId = 1L, weekday = 3, startSlot = 1, endSlot = 1, weekMode = WeekMode.ODD_WEEKS),
            // 双周
            CourseOccurrence(id = 3L, courseId = 2L, weekday = 5, startSlot = 1, endSlot = 1, weekMode = WeekMode.EVEN_WEEKS),
            // 自定义周次
            CourseOccurrence(id = 4L, courseId = 2L, weekday = 7, startSlot = 1, endSlot = 1, weekMode = WeekMode.CUSTOM, customWeeksJson = "[2,4,6]"),
        ),
        settings = AppSettings(
            currentCourseTableId = 1L,
            themeMode = ThemeMode.DARK,
            dynamicColorEnabled = true,
            cardCornerRadius = 8f,
            cardSpacing = 6f,
            timetableRowHeight = 100f,
            cardOpacity = 30,
            showTeacher = false,
            showLocation = true,
            showTime = false,
            remarkPreviewLines = 1,
        ),
    )

    private fun corrupt(original: BackupData, transform: (JSONObject) -> JSONObject): String {
        val root = JSONObject(BackupJson.toJson(original))
        return transform(root).toString()
    }

    @Test
    fun `序列化再反序列化_全量数据无损`() {
        val original = sampleData()
        val json = BackupJson.toJson(original)
        val parsed = BackupJson.fromJson(json)

        assertEquals(original, parsed)
        assertEquals(1, parsed.formatVersion)
        assertEquals("高等数学", parsed.courses.first { it.id == 1L }.name)
        assertEquals("记得带教材\n下周期中考试", parsed.courses.first { it.id == 1L }.remark)
        assertEquals("", parsed.courses.first { it.id == 2L }.remark)
        assertEquals(WeekMode.ODD_WEEKS, parsed.occurrences.first { it.id == 2L }.weekMode)
        assertEquals(WeekMode.EVEN_WEEKS, parsed.occurrences.first { it.id == 3L }.weekMode)
        assertEquals("[2,4,6]", parsed.occurrences.first { it.id == 4L }.customWeeksJson)
        assertEquals(ThemeMode.DARK, parsed.settings.themeMode)
        assertEquals(false, parsed.settings.showTeacher)
    }

    @Test
    fun `当前课表id为空_序列化反序列化保持null`() {
        val data = sampleData().copy(settings = AppSettings(currentCourseTableId = null))
        val parsed = BackupJson.fromJson(BackupJson.toJson(data))
        assertEquals(null, parsed.settings.currentCourseTableId)
    }

    @Test
    fun `格式版本过高_拒绝`() {
        val json = corrupt(sampleData()) { it.put("formatVersion", 99) }
        assertThrows(BackupFormatException::class.java) { BackupJson.fromJson(json) }
    }

    @Test
    fun `格式版本缺失_拒绝`() {
        val json = corrupt(sampleData()) { it.apply { remove("formatVersion") } }
        assertThrows(BackupFormatException::class.java) { BackupJson.fromJson(json) }
    }

    @Test
    fun `缺少必填数组_拒绝`() {
        val json = corrupt(sampleData()) { it.apply { remove("courses") } }
        assertThrows(BackupFormatException::class.java) { BackupJson.fromJson(json) }
    }

    @Test
    fun `缺少settings_拒绝`() {
        val json = corrupt(sampleData()) { it.apply { remove("settings") } }
        assertThrows(BackupFormatException::class.java) { BackupJson.fromJson(json) }
    }

    @Test
    fun `非法weekMode_拒绝`() {
        val json = corrupt(sampleData()) { root ->
            root.getJSONArray("occurrences").getJSONObject(0).put("weekMode", "SOMETIMES")
            root
        }
        assertThrows(BackupFormatException::class.java) { BackupJson.fromJson(json) }
    }

    @Test
    fun `课程引用不存在的课表_拒绝`() {
        val json = corrupt(sampleData()) { root ->
            root.getJSONArray("courses").getJSONObject(0).put("courseTableId", 999)
            root
        }
        assertThrows(BackupFormatException::class.java) { BackupJson.fromJson(json) }
    }

    @Test
    fun `上课安排引用不存在的课程_拒绝`() {
        val json = corrupt(sampleData()) { root ->
            root.getJSONArray("occurrences").getJSONObject(0).put("courseId", 999)
            root
        }
        assertThrows(BackupFormatException::class.java) { BackupJson.fromJson(json) }
    }

    @Test
    fun `星期越界_拒绝`() {
        val json = corrupt(sampleData()) { root ->
            root.getJSONArray("occurrences").getJSONObject(0).put("weekday", 8)
            root
        }
        assertThrows(BackupFormatException::class.java) { BackupJson.fromJson(json) }
    }

    @Test
    fun `结束大节早于起始大节_拒绝`() {
        val json = corrupt(sampleData()) { root ->
            root.getJSONArray("occurrences").getJSONObject(0).put("startSlot", 3).put("endSlot", 2)
            root
        }
        assertThrows(BackupFormatException::class.java) { BackupJson.fromJson(json) }
    }

    @Test
    fun `非JSON文本_拒绝`() {
        assertThrows(BackupFormatException::class.java) { BackupJson.fromJson("不是JSON内容") }
    }

    @Test
    fun `自定义周次文本保留原样`() {
        val parsed = BackupJson.fromJson(BackupJson.toJson(sampleData()))
        val custom = parsed.occurrences.first { it.weekMode == WeekMode.CUSTOM }
        assertEquals("[2,4,6]", custom.customWeeksJson)
    }
}