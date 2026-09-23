package com.gdufs.schedule.data.model

/**
 * 周次重复模式。
 * Room 以枚举名(String)存储。
 */
enum class WeekMode {
    /** 每周 */
    EVERY_WEEK,

    /** 单周 */
    ODD_WEEKS,

    /** 双周 */
    EVEN_WEEKS,

    /** 自定义周次,周次列表存于 customWeeksJson */
    CUSTOM,
}