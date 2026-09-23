package com.gdufs.schedule.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 课程。属于某个课程表。
 * 上课时间不直接存在课程上,而是通过 [CourseOccurrence] 关联时间段。
 * 删除课程表时级联删除课程,删除课程时级联删除其上课安排。
 */
@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = CourseTable::class,
            parentColumns = ["id"],
            childColumns = ["courseTableId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("courseTableId")],
)
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 所属课程表 id */
    val courseTableId: Long,
    /** 课程名称 */
    val name: String,
    /** 授课教师 */
    val teacher: String = "",
    /** 上课地点 */
    val location: String = "",
    /** 折叠备注 */
    val remark: String = "",
    /** 卡片颜色,ARGB 格式 */
    val colorArgb: Int = DEFAULT_COLOR_ARGB,
    /** 是否启用(不参与课表展示) */
    val isEnabled: Boolean = true,
    /** 创建时间,epoch 毫秒 */
    val createdAt: Long = System.currentTimeMillis(),
    /** 更新时间,epoch 毫秒 */
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        /** 默认卡片颜色:靛蓝 */
        const val DEFAULT_COLOR_ARGB: Int = 0xFF4C6EF5.toInt()
    }
}