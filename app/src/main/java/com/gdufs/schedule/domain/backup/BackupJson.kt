package com.gdufs.schedule.domain.backup

import com.gdufs.schedule.data.model.AppSettings
import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.data.model.ThemeMode
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.data.model.TimeSlotScheme
import com.gdufs.schedule.data.model.WeekMode
import com.gdufs.schedule.domain.schedule.WeekRule
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 备份 JSON 的编解码与校验(领域层,不访问数据库)。
 * 使用 org.json(Android 内置,JVM 单测由测试依赖提供;未来迁移 KMP 时替换为跨平台 JSON 库)。
 *
 * 安全约定:[fromJson] 完成全部解析与校验(结构、类型、版本、外键引用一致性)后才返回,
 * 任何问题都抛出 [BackupFormatException];调用方在异常时不写库,故错误文件不会破坏已有数据。
 */
object BackupJson {

    private const val KEY_FORMAT_VERSION = "formatVersion"
    private const val KEY_EXPORTED_AT = "exportedAt"
    private const val KEY_TABLES = "courseTables"
    private const val KEY_SCHEMES = "schemes"
    private const val KEY_TIME_SLOTS = "timeSlots"
    private const val KEY_COURSES = "courses"
    private const val KEY_OCCURRENCES = "occurrences"
    private const val KEY_SETTINGS = "settings"

    // ---------- 序列化 ----------

    fun toJson(data: BackupData): String {
        val root = JSONObject()
        root.put(KEY_FORMAT_VERSION, data.formatVersion)
        root.put(KEY_EXPORTED_AT, data.exportedAtEpochMillis)
        root.put(KEY_TABLES, JSONArray().apply { data.courseTables.forEach { put(tableToJson(it)) } })
        root.put(KEY_SCHEMES, JSONArray().apply { data.schemes.forEach { put(schemeToJson(it)) } })
        root.put(KEY_TIME_SLOTS, JSONArray().apply { data.timeSlots.forEach { put(slotToJson(it)) } })
        root.put(KEY_COURSES, JSONArray().apply { data.courses.forEach { put(courseToJson(it)) } })
        root.put(KEY_OCCURRENCES, JSONArray().apply { data.occurrences.forEach { put(occurrenceToJson(it)) } })
        root.put(KEY_SETTINGS, settingsToJson(data.settings))
        return root.toString(2)
    }

    private fun tableToJson(it: CourseTable) = JSONObject().apply {
        put("id", it.id)
        put("name", it.name)
        put("termStartDate", it.termStartDate)
        put("totalWeeks", it.totalWeeks)
        put("firstDayOfWeek", it.firstDayOfWeek)
        put("timeSlotSchemeId", it.timeSlotSchemeId)
        put("createdAt", it.createdAt)
        put("updatedAt", it.updatedAt)
    }

    private fun schemeToJson(it: TimeSlotScheme) = JSONObject().apply {
        put("id", it.id)
        put("name", it.name)
    }

    private fun slotToJson(it: TimeSlot) = JSONObject().apply {
        put("id", it.id)
        put("schemeId", it.schemeId)
        put("slotNumber", it.slotNumber)
        put("displayName", it.displayName)
        put("startTime", it.startTime)
        put("endTime", it.endTime)
    }

    private fun courseToJson(it: Course) = JSONObject().apply {
        put("id", it.id)
        put("courseTableId", it.courseTableId)
        put("name", it.name)
        put("teacher", it.teacher)
        put("location", it.location)
        put("remark", it.remark)
        put("colorArgb", it.colorArgb)
        put("isEnabled", it.isEnabled)
        put("createdAt", it.createdAt)
        put("updatedAt", it.updatedAt)
    }

    private fun occurrenceToJson(it: CourseOccurrence) = JSONObject().apply {
        put("id", it.id)
        put("courseId", it.courseId)
        put("weekday", it.weekday)
        put("startSlot", it.startSlot)
        put("endSlot", it.endSlot)
        put("weekMode", it.weekMode.name)
        put("customWeeksJson", it.customWeeksJson)
    }

    private fun settingsToJson(it: AppSettings) = JSONObject().apply {
        put("currentCourseTableId", it.currentCourseTableId ?: JSONObject.NULL)
        put("themeMode", it.themeMode.name)
        put("dynamicColorEnabled", it.dynamicColorEnabled)
        put("cardCornerRadius", it.cardCornerRadius.toDouble())
        put("cardSpacing", it.cardSpacing.toDouble())
        put("timetableRowHeight", it.timetableRowHeight.toDouble())
        put("cardOpacity", it.cardOpacity)
        put("showTeacher", it.showTeacher)
        put("showLocation", it.showLocation)
        put("showTime", it.showTime)
        put("remarkPreviewLines", it.remarkPreviewLines)
    }

    // ---------- 反序列化与校验 ----------

    fun fromJson(text: String): BackupData {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupFormatException("备份文件不是有效的 JSON 对象", e)
        }
        val version = root.optInt(KEY_FORMAT_VERSION, -1)
        if (version < 1 || version > BackupData.FORMAT_VERSION) {
            throw BackupFormatException("不支持的备份格式版本: $version (支持 1..${BackupData.FORMAT_VERSION})")
        }
        val exportedAt = root.optLong(KEY_EXPORTED_AT, -1L)
        if (exportedAt < 0) throw BackupFormatException("缺少导出时间字段 exportedAt")

        val tables = parseArray(root, KEY_TABLES).map { tableFromJson(it) }
        val schemes = parseArray(root, KEY_SCHEMES).map { schemeFromJson(it) }
        val timeSlots = parseArray(root, KEY_TIME_SLOTS).map { slotFromJson(it) }
        val courses = parseArray(root, KEY_COURSES).map { courseFromJson(it) }
        val occurrences = parseArray(root, KEY_OCCURRENCES).map { occurrenceFromJson(it) }
        val settings = settingsFromJson(root.optJSONObject(KEY_SETTINGS) ?: throw BackupFormatException("缺少 settings 字段"))

        // 外键引用一致性预检:错误文件在此被拦截,不会进入写库流程
        validateReferences(tables, schemes, timeSlots, courses, occurrences)

        return BackupData(
            formatVersion = version,
            exportedAtEpochMillis = exportedAt,
            courseTables = tables,
            schemes = schemes,
            timeSlots = timeSlots,
            courses = courses,
            occurrences = occurrences,
            settings = settings,
        )
    }

    private fun parseArray(root: JSONObject, key: String): List<JSONObject> {
        val array = root.optJSONArray(key) ?: throw BackupFormatException("缺少 $key 数组")
        return (0 until array.length()).map { index ->
            array.optJSONObject(index) ?: throw BackupFormatException("$key[$index] 不是 JSON 对象")
        }
    }

    private fun tableFromJson(obj: JSONObject): CourseTable = CourseTable(
        id = obj.optLong("id", 0L),
        name = obj.str("name"),
        termStartDate = obj.optLong("termStartDate", -1L).also { if (it < 0) throw BackupFormatException("课表 termStartDate 非法") },
        totalWeeks = obj.optInt("totalWeeks", -1).also { if (it < 1) throw BackupFormatException("课表 totalWeeks 必须 >= 1") },
        firstDayOfWeek = obj.optInt("firstDayOfWeek", -1).also { if (it !in 1..7) throw BackupFormatException("课表 firstDayOfWeek 必须在 1..7") },
        timeSlotSchemeId = obj.optLong("timeSlotSchemeId", -1L),
        createdAt = obj.optLong("createdAt", 0L),
        updatedAt = obj.optLong("updatedAt", 0L),
    )

    private fun schemeFromJson(obj: JSONObject): TimeSlotScheme = TimeSlotScheme(
        id = obj.optLong("id", 0L),
        name = obj.str("name"),
    )

    private fun slotFromJson(obj: JSONObject): TimeSlot = TimeSlot(
        id = obj.optLong("id", 0L),
        schemeId = obj.optLong("schemeId", -1L),
        slotNumber = obj.optInt("slotNumber", -1).also { if (it < 1) throw BackupFormatException("时间段 slotNumber 必须 >= 1") },
        displayName = obj.str("displayName"),
        startTime = obj.str("startTime"),
        endTime = obj.str("endTime"),
    )

    private fun courseFromJson(obj: JSONObject): Course = Course(
        id = obj.optLong("id", 0L),
        courseTableId = obj.optLong("courseTableId", -1L),
        name = obj.str("name"),
        teacher = obj.optString("teacher", ""),
        location = obj.optString("location", ""),
        remark = obj.optString("remark", ""),
        colorArgb = obj.optInt("colorArgb", Course.DEFAULT_COLOR_ARGB),
        isEnabled = obj.optBoolean("isEnabled", true),
        createdAt = obj.optLong("createdAt", 0L),
        updatedAt = obj.optLong("updatedAt", 0L),
    )

    private fun occurrenceFromJson(obj: JSONObject): CourseOccurrence {
        val weekMode = try {
            WeekMode.valueOf(obj.str("weekMode"))
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("非法 weekMode: ${obj.optString("weekMode")}", e)
        }
        val customWeeksJson = obj.optString("customWeeksJson", "")
        // 自定义周次文本必须可解析(空集合视为合法)
        WeekRule.parseCustomWeeks(if (weekMode == WeekMode.CUSTOM) customWeeksJson else "")
        val startSlot = obj.optInt("startSlot", -1).also { if (it < 1) throw BackupFormatException("上课安排 startSlot 必须 >= 1") }
        val endSlot = obj.optInt("endSlot", -1).also { if (it < 1) throw BackupFormatException("上课安排 endSlot 必须 >= 1") }
        if (endSlot < startSlot) throw BackupFormatException("上课安排 endSlot 必须 >= startSlot")
        return CourseOccurrence(
            id = obj.optLong("id", 0L),
            courseId = obj.optLong("courseId", -1L),
            weekday = obj.optInt("weekday", -1).also { if (it !in 1..7) throw BackupFormatException("上课安排 weekday 必须在 1..7") },
            startSlot = startSlot,
            endSlot = endSlot,
            weekMode = weekMode,
            customWeeksJson = customWeeksJson,
        )
    }

    private fun settingsFromJson(obj: JSONObject): AppSettings {
        val themeMode = try {
            ThemeMode.valueOf(obj.str("themeMode"))
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("非法 themeMode: ${obj.optString("themeMode")}", e)
        }
        val rawTableId = obj.opt("currentCourseTableId")
        val currentTableId = if (rawTableId == null || rawTableId === JSONObject.NULL) null else (rawTableId as? Number)?.toLong()
        return AppSettings(
            id = AppSettings.SETTINGS_ROW_ID,
            currentCourseTableId = currentTableId,
            themeMode = themeMode,
            dynamicColorEnabled = obj.optBoolean("dynamicColorEnabled", false),
            cardCornerRadius = obj.optDouble("cardCornerRadius", 12.0).toFloat(),
            cardSpacing = obj.optDouble("cardSpacing", 4.0).toFloat(),
            timetableRowHeight = obj.optDouble("timetableRowHeight", 88.0).toFloat(),
            cardOpacity = obj.optInt("cardOpacity", 18),
            showTeacher = obj.optBoolean("showTeacher", true),
            showLocation = obj.optBoolean("showLocation", true),
            showTime = obj.optBoolean("showTime", true),
            remarkPreviewLines = obj.optInt("remarkPreviewLines", 2),
        )
    }

    /** 校验外键引用一致性:引用缺失的整体视为损坏文件 */
    private fun validateReferences(
        tables: List<CourseTable>,
        schemes: List<TimeSlotScheme>,
        timeSlots: List<TimeSlot>,
        courses: List<Course>,
        occurrences: List<CourseOccurrence>,
    ) {
        val schemeIds = schemes.map { it.id }.toSet()
        val tableIds = tables.map { it.id }.toSet()
        val courseIds = courses.map { it.id }.toSet()
        tables.forEach { table ->
            if (table.timeSlotSchemeId !in schemeIds) {
                throw BackupFormatException("课表 ${table.name} 引用的作息方案不存在")
            }
        }
        timeSlots.forEach { slot ->
            if (slot.schemeId !in schemeIds) {
                throw BackupFormatException("时间段 ${slot.displayName} 引用的作息方案不存在")
            }
        }
        courses.forEach { course ->
            if (course.courseTableId !in tableIds) {
                throw BackupFormatException("课程 ${course.name} 引用的课表不存在")
            }
        }
        occurrences.forEach { occurrence ->
            if (occurrence.courseId !in courseIds) {
                throw BackupFormatException("上课安排引用的课程不存在")
            }
        }
    }

    /** 读取必填字符串,缺失或类型错误时报格式异常 */
    private fun JSONObject.str(key: String): String {
        val value = opt(key) ?: throw BackupFormatException("缺少字段 $key")
        if (value !is String || value.isBlank()) throw BackupFormatException("字段 $key 必须是非空字符串")
        return value
    }
}