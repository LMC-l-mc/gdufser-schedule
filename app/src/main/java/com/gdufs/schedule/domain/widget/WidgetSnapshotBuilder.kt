package com.gdufs.schedule.domain.widget

import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.data.model.CourseWithOccurrences
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.domain.schedule.ScheduleCalculator
import com.gdufs.schedule.domain.schedule.ScheduleQuery
import com.gdufs.schedule.domain.schedule.TodaySchedule
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 小组件今日快照中的一条课程信息(纯数据,展示文本由小组件端用资源拼装) */
data class WidgetSnapshotItem(
    val courseId: Long,
    val name: String,
    val teacher: String,
    val location: String,
    /** 大节标签,单节"第一大节",跨节"第一至第二大节" */
    val slotLabel: String,
    /** 实际时间范围文本,例如"08:30–09:50" */
    val timeRange: String,
    val hasRemark: Boolean,
)

/** 小组件今日快照内容 */
data class WidgetSnapshotContent(
    val dateEpochMillis: Long,
    /** 日期标签,例如"9月22日" */
    val dateLabel: String,
    /** 星期序号(1=周一 … 7=周日),由小组件端映射星期名称资源 */
    val weekdayIndex: Int,
    /** 今日全部课程(按开始大节升序) */
    val items: List<WidgetSnapshotItem>,
    /** 下一节课程在 [items] 中的下标;无下一节时为空 */
    val nextItemIndex: Int?,
)

/**
 * 小组件快照构建器(纯 Kotlin,不依赖 Android 框架):
 * 基于主库已读出的课程列表计算今日条目与下一节,小组件端不再做任何课表计算。
 */
object WidgetSnapshotBuilder {

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

    /**
     * 计算某个日期的快照内容。学期未开始/已结束(无有效课程)时返回空条目列表;
     * 跨大节时间范围取起始大节开始到结束大节结束。
     */
    fun build(
        table: CourseTable,
        courses: List<CourseWithOccurrences>,
        timeSlots: List<TimeSlot>,
        dateEpochMillis: Long,
        now: LocalTime,
    ): WidgetSnapshotContent {
        val entries = ScheduleQuery.coursesOnDay(
            courses = courses,
            dateEpochMillis = dateEpochMillis,
            termStartEpochMillis = table.termStartDate,
            firstDayOfWeek = table.firstDayOfWeek,
            totalWeeks = table.totalWeeks,
        )
        val nowMinutes = now.hour * 60 + now.minute
        var nextItemIndex: Int? = null
        val items = mutableListOf<WidgetSnapshotItem>()
        entries.forEach { entry ->
            val item = itemOf(
                courseId = entry.course.id,
                name = entry.course.name,
                teacher = entry.course.teacher,
                location = entry.course.location,
                remark = entry.course.remark,
                startSlot = entry.occurrence.startSlot,
                endSlot = entry.occurrence.endSlot,
                timeSlots = timeSlots,
            ) ?: return@forEach
            if (nextItemIndex == null) {
                val startTime = timeSlots.firstOrNull { it.slotNumber == entry.occurrence.startSlot }?.startTime
                val minutes = startTime?.let { TodaySchedule.parseMinutes(it) }
                if (minutes != null && minutes > nowMinutes) {
                    nextItemIndex = items.size
                }
            }
            items.add(item)
        }

        val date = ScheduleCalculator.toLocalDate(dateEpochMillis)
        return WidgetSnapshotContent(
            dateEpochMillis = dateEpochMillis,
            dateLabel = DATE_FORMAT.format(date),
            weekdayIndex = ScheduleCalculator.weekdayOf(dateEpochMillis),
            items = items,
            nextItemIndex = nextItemIndex,
        )
    }

    private fun itemOf(
        courseId: Long,
        name: String,
        teacher: String,
        location: String,
        remark: String,
        startSlot: Int,
        endSlot: Int,
        timeSlots: List<TimeSlot>,
    ): WidgetSnapshotItem? {
        val start = timeSlots.firstOrNull { it.slotNumber == startSlot } ?: return null
        val end = timeSlots.firstOrNull { it.slotNumber == endSlot } ?: return null
        val slotLabel = if (startSlot == endSlot) {
            start.displayName
        } else {
            "${start.displayName.removeSuffix("大节")}至${end.displayName}"
        }
        return WidgetSnapshotItem(
            courseId = courseId,
            name = name,
            teacher = teacher,
            location = location,
            slotLabel = slotLabel,
            timeRange = "${start.startTime}–${end.endTime}",
            hasRemark = remark.isNotBlank(),
        )
    }

    /** 快照内容序列化为小组件库 payload(结构化 JSON,展示文本不在此固定) */
    fun toPayloadJson(content: WidgetSnapshotContent): String {
        val root = JSONObject()
        root.put("dateEpochMillis", content.dateEpochMillis)
        root.put("dateLabel", content.dateLabel)
        root.put("weekdayIndex", content.weekdayIndex)
        root.put("nextItemIndex", content.nextItemIndex ?: -1)
        root.put("items", JSONArray().apply {
            content.items.forEach { item ->
                put(
                    JSONObject().apply {
                        put("courseId", item.courseId)
                        put("name", item.name)
                        put("teacher", item.teacher)
                        put("location", item.location)
                        put("slotLabel", item.slotLabel)
                        put("timeRange", item.timeRange)
                        put("hasRemark", item.hasRemark)
                    }
                )
            }
        })
        return root.toString()
    }

    /** 从 payload JSON 还原快照内容(小组件端仅做反序列化,不做计算) */
    fun fromPayloadJson(payload: String): WidgetSnapshotContent {
        val root = JSONObject(payload)
        val itemsArray = root.getJSONArray("items")
        val items = (0 until itemsArray.length()).map { index ->
            val obj = itemsArray.getJSONObject(index)
            WidgetSnapshotItem(
                courseId = obj.getLong("courseId"),
                name = obj.getString("name"),
                teacher = obj.optString("teacher", ""),
                location = obj.optString("location", ""),
                slotLabel = obj.getString("slotLabel"),
                timeRange = obj.getString("timeRange"),
                hasRemark = obj.optBoolean("hasRemark", false),
            )
        }
        val nextIndex = root.getInt("nextItemIndex")
        return WidgetSnapshotContent(
            dateEpochMillis = root.getLong("dateEpochMillis"),
            dateLabel = root.getString("dateLabel"),
            weekdayIndex = root.getInt("weekdayIndex"),
            items = items,
            nextItemIndex = nextIndex.takeIf { it >= 0 },
        )
    }
}