package com.gdufs.schedule.data.repository

import androidx.room.withTransaction
import com.gdufs.schedule.data.db.AppDatabase
import com.gdufs.schedule.data.db.CourseTableDao
import com.gdufs.schedule.data.db.TimeSlotDao
import com.gdufs.schedule.data.db.TimeSlotSchemeDao
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.data.model.TimeSlotScheme
import kotlinx.coroutines.flow.Flow

/** 作息方案删除结果:成功删除,或被课表引用而拒绝 */
sealed interface SchemeDeleteResult {
    data object Deleted : SchemeDeleteResult
    data object InUse : SchemeDeleteResult
}

/**
 * 作息方案与时间段数据唯一入口。
 * 所有数据库操作必须经过 Repository,禁止 UI 直接访问 DAO。
 * 默认作息初始化由服务层(DefaultScheduleInitializer)负责,本接口提供用户侧的读写。
 */
interface TimeSlotRepository {

    fun observeSchemes(): Flow<List<TimeSlotScheme>>

    /** 按大节序号升序返回方案内时间段 */
    fun observeTimeSlots(schemeId: Long): Flow<List<TimeSlot>>

    /** 新建作息方案,返回新方案 id */
    suspend fun createScheme(name: String): Long

    /**
     * 复制作息方案:深拷贝方案及其全部时间段。
     * [newName] 为副本名称。返回新方案 id。
     */
    suspend fun duplicateScheme(schemeId: Long, newName: String): Long

    /**
     * 删除作息方案(级联删除其时间段)。
     * 若方案仍被课表引用,返回 [SchemeDeleteResult.InUse] 且不删除。
     */
    suspend fun deleteScheme(schemeId: Long): SchemeDeleteResult

    /** 方案内已有时间段数量,用于”固定默认大节”的判断(阶段边界保护) */
    suspend fun timeSlotCount(schemeId: Long): Int

    /**
     * 在方案末尾追加时间段(大节序号自动取当前最大值 + 1),返回新时间段 id。
     * 开始/结束时间必须在调用前校验通过:开始早于结束、且与已有时间段不重叠。
     */
    suspend fun addTimeSlot(schemeId: Long, displayName: String, startTime: String, endTime: String): Long

    suspend fun updateTimeSlot(slot: TimeSlot)

    /** 删除时间段,并把剩余时间段的大节序号重新连续编号(默认命名随序号更新) */
    suspend fun deleteTimeSlot(slot: TimeSlot)

    /**
     * 时间段排序:把 [fromIndex] 处的时段移动到 [toIndex] 处(均为按大节序号升序后的下标),
     * 移动后重写全部大节序号为连续编号。
     */
    suspend fun moveTimeSlot(schemeId: Long, fromIndex: Int, toIndex: Int)
}

class DefaultTimeSlotRepository(
    private val database: AppDatabase,
    private val schemeDao: TimeSlotSchemeDao,
    private val slotDao: TimeSlotDao,
    private val courseTableDao: CourseTableDao,
) : TimeSlotRepository {

    override fun observeSchemes(): Flow<List<TimeSlotScheme>> = schemeDao.observeAll()

    override fun observeTimeSlots(schemeId: Long): Flow<List<TimeSlot>> =
        slotDao.observeByScheme(schemeId)

    override suspend fun createScheme(name: String): Long =
        schemeDao.insert(TimeSlotScheme(name = name))

    override suspend fun duplicateScheme(schemeId: Long, newName: String): Long =
        database.withTransaction {
            val source = requireNotNull(schemeDao.getByIdOnce(schemeId)) {
                "要复制的作息方案不存在: id=$schemeId"
            }
            val newId = schemeDao.insert(TimeSlotScheme(name = newName))
            slotDao.insertAll(
                slotDao.getBySchemeOnce(schemeId).map { it.copy(id = 0L, schemeId = newId) }
            )
            newId
        }

    override suspend fun deleteScheme(schemeId: Long): SchemeDeleteResult {
        if (courseTableDao.countByScheme(schemeId) > 0) return SchemeDeleteResult.InUse
        val scheme = schemeDao.getByIdOnce(schemeId) ?: return SchemeDeleteResult.Deleted
        schemeDao.delete(scheme) // 外键级联删除其时间段
        return SchemeDeleteResult.Deleted
    }

    override suspend fun timeSlotCount(schemeId: Long): Int =
        slotDao.getBySchemeOnce(schemeId).size

    override suspend fun addTimeSlot(
        schemeId: Long,
        displayName: String,
        startTime: String,
        endTime: String,
    ): Long {
        val nextNumber = (slotDao.getBySchemeOnce(schemeId).maxOfOrNull { it.slotNumber } ?: 0) + 1
        return slotDao.insert(
            TimeSlot(
                schemeId = schemeId,
                slotNumber = nextNumber,
                displayName = displayName,
                startTime = startTime,
                endTime = endTime,
            )
        )
    }

    override suspend fun updateTimeSlot(slot: TimeSlot) {
        slotDao.update(slot)
    }

    override suspend fun deleteTimeSlot(slot: TimeSlot) {
        slotDao.delete(slot)
        renumberSlots(slot.schemeId)
    }

    override suspend fun moveTimeSlot(schemeId: Long, fromIndex: Int, toIndex: Int) {
        val slots = slotDao.getBySchemeOnce(schemeId).toMutableList()
        if (fromIndex !in slots.indices || toIndex !in slots.indices || fromIndex == toIndex) return
        val moved = slots.removeAt(fromIndex)
        slots.add(toIndex, moved)
        renumber(slots)
    }

    private suspend fun renumberSlots(schemeId: Long) {
        renumber(slotDao.getBySchemeOnce(schemeId))
    }

    private suspend fun renumber(slots: List<TimeSlot>) {
        slotDao.updateAll(
            slots.mapIndexed { index, slot ->
                val newNumber = index + 1
                val renamed = if (slot.displayName == defaultDisplayName(slot.slotNumber)) {
                    slot.copy(slotNumber = newNumber, displayName = defaultDisplayName(newNumber))
                } else {
                    slot.copy(slotNumber = newNumber)
                }
                renamed
            }
        )
    }

    private fun defaultDisplayName(slotNumber: Int): String {
        val numeral = CHINESE_NUMERALS.getOrNull(slotNumber - 1) ?: slotNumber.toString()
        return "第${numeral}大节"
    }

    private companion object {
        /** 默认大节名称使用中文数字(与默认作息一致),超过十个回退阿拉伯数字 */
        val CHINESE_NUMERALS = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
    }
}