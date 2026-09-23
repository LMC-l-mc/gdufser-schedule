package com.gdufs.schedule.ui.screen.timetable

import com.gdufs.schedule.domain.schedule.WeekCourseEntry

/**
 * 周课表网格中一门课程的定位信息:
 * [lane]/[laneCount] 为同列重叠课程分栏结果,
 * [startRow] 为起始大节对应的行下标(0 起),[rowSpan] 为跨行数。
 */
data class PlacedCourse(
    val entry: WeekCourseEntry,
    val lane: Int,
    val laneCount: Int,
    val startRow: Int,
    val rowSpan: Int,
)

private data class ColumnItem(
    val entry: WeekCourseEntry,
    val startRow: Int,
    val endRow: Int,
)

/**
 * 把同一列(同一天)的课程安排转换为网格定位信息(纯 Kotlin,便于单元测试):
 * - 起始大节对应的行下标 = startSlot - firstSlotNumber;
 * - 跨大节安排的行数 = endSlot - startSlot + 1;
 * - 同星期且大节区间重叠的安排分到不同车道(lane),车道数即该列最大并行课程数;
 * - 完全超出作息范围(1..slotCount)的安排被丢弃,部分超出时裁剪行范围;
 * - 需要保证起始大节早于结束大节,该不变量由表单校验层保证。
 */
fun layoutDayColumn(
    entries: List<WeekCourseEntry>,
    firstSlotNumber: Int,
    slotCount: Int,
): List<PlacedCourse> {
    if (slotCount <= 0) return emptyList()

    val items = entries
        .mapNotNull { entry ->
            val occurrence = entry.occurrence
            val startRow = occurrence.startSlot - firstSlotNumber
            val endRow = occurrence.endSlot - firstSlotNumber
            if (endRow < 0 || startRow >= slotCount) {
                null // 安排完全落在作息范围之外
            } else {
                ColumnItem(
                    entry = entry,
                    startRow = startRow.coerceAtLeast(0),
                    endRow = endRow.coerceAtMost(slotCount - 1),
                )
            }
        }
        .sortedBy { it.startRow }

    // 车道分配:每根车道记录最后一门课程占用的结束大节序号
    val lanes = mutableListOf<Int>()
    val placed = items.map { item ->
        val occurrence = item.entry.occurrence
        val lane = lanes.indexOfFirst { it < occurrence.startSlot }
        val chosenLane = if (lane == -1) {
            lanes.add(occurrence.endSlot)
            lanes.size - 1
        } else {
            lanes[lane] = occurrence.endSlot
            lane
        }
        PlacedCourse(
            entry = item.entry,
            lane = chosenLane,
            laneCount = 0, // 总车道数需在所有安排处理完后确定,先占位
            startRow = item.startRow,
            rowSpan = item.endRow - item.startRow + 1,
        )
    }
    val laneCount = lanes.size
    return placed.map { it.copy(laneCount = laneCount) }
}