package com.gdufs.schedule.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 课程上课安排。一门课可以有多个时间段(例如理论课 + 实验课)。
 * 删除课程时级联删除其全部上课安排。
 */
@Entity(
    tableName = "course_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("courseId")],
)
data class CourseOccurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 所属课程 id */
    val courseId: Long,
    /** 星期几,1=周一 … 7=周日 */
    val weekday: Int,
    /** 起始大节序号,引用 TimeSlot.slotNumber */
    val startSlot: Int,
    /** 结束大节序号(含),引用 TimeSlot.slotNumber */
    val endSlot: Int,
    /** 周次模式 */
    val weekMode: WeekMode = WeekMode.EVERY_WEEK,
    /** 自定义周次,JSON 数组字符串,例如 "[1,2,5]",仅 weekMode=CUSTOM 时有效 */
    val customWeeksJson: String = "",
)