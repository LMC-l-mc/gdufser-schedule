package com.gdufs.schedule.data.db

import com.gdufs.schedule.data.model.TimeSlot

/**
 * 固定默认作息定义,首次启动初始化时写入数据库。
 * 仅当库中不存在任何作息方案时生效,后续修改不影响已初始化数据。
 */
object DefaultSchedule {

    /** 默认作息方案名称 */
    const val DEFAULT_SCHEME_NAME = "默认作息"

    /**
     * 默认时间段规格,按大节序号升序。
     */
    data class SlotSpec(
        val slotNumber: Int,
        val displayName: String,
        val startTime: String,
        val endTime: String,
    )

    /** 固定默认作息:七个大节 */
    val DEFAULT_SLOT_SPECS: List<SlotSpec> = listOf(
        SlotSpec(1, "第一大节", "08:30", "09:50"),
        SlotSpec(2, "第二大节", "10:10", "12:15"),
        SlotSpec(3, "第三大节", "12:30", "13:50"),
        SlotSpec(4, "第四大节", "14:00", "15:20"),
        SlotSpec(5, "第五大节", "15:40", "17:00"),
        SlotSpec(6, "第六大节", "18:30", "19:50"),
        SlotSpec(7, "第七大节", "20:00", "21:20"),
    )

    /** 根据方案 id 生成默认时间段实体列表 */
    fun defaultSlotsFor(schemeId: Long): List<TimeSlot> =
        DEFAULT_SLOT_SPECS.map { spec ->
            TimeSlot(
                schemeId = schemeId,
                slotNumber = spec.slotNumber,
                displayName = spec.displayName,
                startTime = spec.startTime,
                endTime = spec.endTime,
            )
        }
}