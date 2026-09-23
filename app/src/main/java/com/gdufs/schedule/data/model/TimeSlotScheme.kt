package com.gdufs.schedule.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 作息方案。一个方案包含多个时间段([TimeSlot])。
 * 课程表([CourseTable])通过 timeSlotSchemeId 引用方案。
 */
@Entity(tableName = "time_slot_schemes")
data class TimeSlotScheme(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 方案名称,例如"我的作息" */
    val name: String,
)