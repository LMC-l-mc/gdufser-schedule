package com.gdufs.schedule.domain.backup

import com.gdufs.schedule.data.model.AppSettings
import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.data.model.TimeSlotScheme

/**
 * JSON 备份的整体数据快照(纯 Kotlin,便于未来迁入 KMP 共享模块)。
 * 覆盖全部课表、作息方案与时间段、课程与上课安排(含周次规则)、外观设置,
 * 并携带格式版本与导出时间。
 */
data class BackupData(
    val formatVersion: Int = FORMAT_VERSION,
    val exportedAtEpochMillis: Long,
    val courseTables: List<CourseTable>,
    val schemes: List<TimeSlotScheme>,
    val timeSlots: List<TimeSlot>,
    val courses: List<Course>,
    val occurrences: List<CourseOccurrence>,
    val settings: AppSettings,
) {
    companion object {
        /** 当前备份格式版本;旧版本文件可读,更高版本视为不兼容 */
        const val FORMAT_VERSION: Int = 1
    }
}

/**
 * 备份文件格式错误(结构缺失、字段类型错误、版本不兼容等)。
 * 解析层抛出此异常时,恢复流程保证尚未写入任何数据,现有数据不受影响。
 */
class BackupFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)