package com.gdufs.schedule.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 课程表(学期表)。一个课程表包含多门课程([Course])。
 * 删除课程表时级联删除其下课程与上课安排。
 */
@Entity(
    tableName = "course_tables",
    foreignKeys = [
        ForeignKey(
            entity = TimeSlotScheme::class,
            parentColumns = ["id"],
            childColumns = ["timeSlotSchemeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("timeSlotSchemeId")],
)
data class CourseTable(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 课程表名称,例如"2025-2026 学年第二学期" */
    val name: String,
    /** 学期开始日期,epoch 毫秒(当天 0 点) */
    val termStartDate: Long,
    /** 学期总周数 */
    val totalWeeks: Int,
    /** 每周第一天:1=周一 … 7=周日 */
    val firstDayOfWeek: Int,
    /** 引用的作息方案 id */
    val timeSlotSchemeId: Long,
    /** 创建时间,epoch 毫秒 */
    val createdAt: Long,
    /** 更新时间,epoch 毫秒 */
    val updatedAt: Long,
)