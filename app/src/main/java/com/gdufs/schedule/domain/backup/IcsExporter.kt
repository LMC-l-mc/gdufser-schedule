package com.gdufs.schedule.domain.backup

import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.data.model.CourseWithOccurrences
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.domain.schedule.ScheduleCalculator
import com.gdufs.schedule.domain.schedule.WeekRule
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ICS(VEVENT)导出器:把课表内的课程转换为 RFC 5545 日历事件(纯 Kotlin)。
 *
 * 规则:
 * - 每门课程的每条上课安排按其周次规则(每周/单周/双周/自定义)在学期内逐周展开为独立事件;
 * - 事件开始/结束时间取自作息方案中大节的实际时间(跨大节 = 起始大节开始 ~ 结束大节结束);
 * - SUMMARY 为课程名称,DESCRIPTION 含教师与备注(有才写),LOCATION 为地点;
 * - 使用本地时区(墙钟时间 + TZID),时间换算无需 UTC,符合课表使用直觉。
 */
object IcsExporter {

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss")

    private const val PROD_ID = "-//gdufs-schedule//Timetable//CN"

    /** 把整张课表导出为 ICS 文本;无事件时返回空的 VCALENDAR 骨架 */
    fun export(
        table: CourseTable,
        courses: List<CourseWithOccurrences>,
        timeSlots: List<TimeSlot>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val lines = mutableListOf<String>()
        lines += "BEGIN:VCALENDAR"
        lines += "VERSION:2.0"
        lines += "PRODID:$PROD_ID"
        lines += "CALSCALE:GREGORIAN"

        courses.asSequence()
            .filter { it.course.isEnabled }
            .forEach { item ->
                val occurrenceSlots = item.occurrences
                occurrenceSlots.forEach { occurrence ->
                    val timeRange = resolveTimeRange(occurrence, timeSlots) ?: return@forEach
                    val activeWeeks = weeksOf(occurrence, table.totalWeeks)
                    activeWeeks.forEach { week ->
                        val date = LocalDate.ofInstant(
                            java.time.Instant.ofEpochMilli(
                                ScheduleCalculator.dateOfWeek(
                                    week,
                                    occurrence.weekday,
                                    table.termStartDate,
                                    table.firstDayOfWeek,
                                    zoneId,
                                )
                            ),
                            zoneId,
                        )
                        lines += "BEGIN:VEVENT"
                        lines += "UID:course-${item.course.id}-occ-${occurrence.id}-w$week@gdufs-schedule"
                        lines += "DTSTAMP:${date.format(DATE_FORMAT)}T000000Z"
                        lines += "DTSTART;TZID=${zoneId.id}:${date.format(DATE_FORMAT)}T${timeRange.first}"
                        lines += "DTEND;TZID=${zoneId.id}:${date.format(DATE_FORMAT)}T${timeRange.second}"
                        lines += "SUMMARY:${escape(item.course.name)}"
                        val description = buildList {
                            if (item.course.teacher.isNotBlank()) add("教师: ${item.course.teacher}")
                            if (item.course.remark.isNotBlank()) add("备注: ${item.course.remark}")
                        }.joinToString("\n")
                        if (description.isNotBlank()) {
                            lines += "DESCRIPTION:${escape(description)}"
                        }
                        if (item.course.location.isNotBlank()) {
                            lines += "LOCATION:${escape(item.course.location)}"
                        }
                        lines += "END:VEVENT"
                    }
                }
            }

        lines += "END:VCALENDAR"
        return foldLines(lines)
    }

    /** 时间段文本("HH:mm")→ ICS 时间("HHmmss");匹配不到起/止大节时返回 null */
    private fun resolveTimeRange(
        occurrence: com.gdufs.schedule.data.model.CourseOccurrence,
        timeSlots: List<TimeSlot>,
    ): Pair<String, String>? {
        val start = timeSlots.firstOrNull { it.slotNumber == occurrence.startSlot } ?: return null
        val end = timeSlots.firstOrNull { it.slotNumber == occurrence.endSlot } ?: return null
        val startIcs = toIcsTime(start.startTime) ?: return null
        val endIcs = toIcsTime(end.endTime) ?: return null
        return startIcs to endIcs
    }

    /** "HH:mm" → "HHmmss";格式非法返回 null */
    fun toIcsTime(text: String): String? {
        val parts = text.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return "${hour.toString().padStart(2, '0')}${minute.toString().padStart(2, '0')}00"
    }

    /** 按周次模式展开学期内的生效周集合(升序,1 起) */
    fun weeksOf(
        occurrence: com.gdufs.schedule.data.model.CourseOccurrence,
        totalWeeks: Int,
    ): List<Int> {
        val customWeeks = WeekRule.parseCustomWeeks(occurrence.customWeeksJson)
        return (1..totalWeeks).filter { week ->
            WeekRule.isActiveInWeek(occurrence.weekMode, customWeeks, week)
        }
    }

    /** 转义 ICS 文本:反斜杠、分号、逗号、换行 */
    private fun escape(text: String): String = buildString {
        text.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                ';' -> append("\\;")
                ',' -> append("\\,")
                '\n' -> append("\\n")
                '\r' -> Unit
                else -> append(char)
            }
        }
    }

    /** RFC 5545 行折行:每行按 UTF-8 最多 75 字节,续行以空格开头 */
    fun foldLines(lines: List<String>): String = lines.joinToString("\r\n") { line ->
        foldLine(line)
    } + "\r\n"

    private fun foldLine(line: String): String {
        val out = StringBuilder()
        var start = 0
        var first = true
        while (start < line.length) {
            if (!first) out.append("\r\n ")
            val limit = if (first) 75 else 74
            val cut = cutChars(line, start, limit)
            val next = if (cut == 0) line.length else start + cut
            out.append(line, start, next)
            start = next
            first = false
        }
        return out.toString()
    }

    /** 从 [from] 起最多取 [maxBytes] 个 UTF-8 字节对应的完整字符数 */
    private fun cutChars(text: String, from: Int, maxBytes: Int): Int {
        var bytes = 0
        var i = from
        while (i < text.length) {
            val charBytes = text.substring(i, i + 1).toByteArray(Charsets.UTF_8).size
            if (bytes + charBytes > maxBytes) break
            bytes += charBytes
            i++
        }
        return i - from
    }
}