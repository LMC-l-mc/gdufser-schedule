package com.gdufs.schedule.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用设置,单行表(仅一行,id 恒为 [SETTINGS_ROW_ID])。
 * 所有读写必须经过 SettingsRepository。
 */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = SETTINGS_ROW_ID,
    /** 当前课程表 id,未选择时为 null */
    val currentCourseTableId: Long? = null,
    /** 主题模式 */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 是否启用动态取色(Material You) */
    val dynamicColorEnabled: Boolean = false,
    /** 课程卡片圆角(dp) */
    val cardCornerRadius: Float = 12f,
    /** 课程卡片间距(dp) */
    val cardSpacing: Float = 4f,
    /** 课表每大节行高(dp) */
    val timetableRowHeight: Float = 88f,
    /** 课程卡片不透明度(0..100,数值越大越实,100 不透明) */
    val cardOpacity: Int = 18,
    /** 是否在课程卡片上显示教师 */
    val showTeacher: Boolean = true,
    /** 是否在课程卡片上显示地点 */
    val showLocation: Boolean = true,
    /** 是否在课程卡片上显示实际时间 */
    val showTime: Boolean = true,
    /** 备注预览最大行数(1 或 2) */
    val remarkPreviewLines: Int = 2,
) {
    companion object {
        /** 单行表固定主键 */
        const val SETTINGS_ROW_ID: Int = 1
    }
}