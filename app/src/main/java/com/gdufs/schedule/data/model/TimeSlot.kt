package com.gdufs.schedule.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 时间段(大节)。属于某个作息方案。
 * slotNumber 从 1 开始递增,课程安排引用该编号表示第几大节。
 */
@Entity(
    tableName = "time_slots",
    foreignKeys = [
        ForeignKey(
            entity = TimeSlotScheme::class,
            parentColumns = ["id"],
            childColumns = ["schemeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("schemeId")],
)
data class TimeSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 所属作息方案 id */
    val schemeId: Long,
    /** 大节序号,1 起 */
    val slotNumber: Int,
    /** 显示名称,例如"第一大节" */
    val displayName: String,
    /** 开始时间,格式 HH:mm */
    val startTime: String,
    /** 结束时间,格式 HH:mm */
    val endTime: String,
)