package com.gdufs.schedule.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.data.model.TimeSlotScheme
import com.gdufs.schedule.data.repository.CourseTableRepository
import com.gdufs.schedule.data.repository.SchemeDeleteResult
import com.gdufs.schedule.data.repository.TimeSlotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 作息方案及其被课表引用的数量,用于删除保护展示 */
data class SchemeUsage(
    val scheme: TimeSlotScheme,
    val usedByTables: Int,
)

/**
 * 作息方案管理 ViewModel:
 * - 暴露方案列表(含被课表引用数量)与某方案的时间段列表;
 * - 所有数据库操作经 Repository。
 */
class TimeSlotSchemeViewModel(
    private val timeSlotRepository: TimeSlotRepository,
    courseTableRepository: CourseTableRepository,
) : ViewModel() {

    val schemeUsages: StateFlow<List<SchemeUsage>> =
        combine(
            timeSlotRepository.observeSchemes(),
            courseTableRepository.observeCourseTables(),
        ) { schemes, tables ->
            schemes.map { scheme ->
                SchemeUsage(
                    scheme = scheme,
                    usedByTables = tables.count { it.timeSlotSchemeId == scheme.id },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeTimeSlots(schemeId: Long): Flow<List<TimeSlot>> =
        timeSlotRepository.observeTimeSlots(schemeId)

    suspend fun createScheme(name: String): Long = timeSlotRepository.createScheme(name)

    suspend fun duplicateScheme(schemeId: Long, newName: String): Long =
        timeSlotRepository.duplicateScheme(schemeId, newName)

    /** 被课表引用时返回 [SchemeDeleteResult.InUse],调用方据此给出提示 */
    suspend fun deleteScheme(schemeId: Long): SchemeDeleteResult =
        timeSlotRepository.deleteScheme(schemeId)

    /** 方案内时间段数量,用于删除方案前的提示 */
    suspend fun timeSlotCount(schemeId: Long): Int =
        timeSlotRepository.timeSlotCount(schemeId)

    suspend fun addTimeSlot(
        schemeId: Long,
        displayName: String,
        startTime: String,
        endTime: String,
    ): Long = timeSlotRepository.addTimeSlot(schemeId, displayName, startTime, endTime)

    suspend fun updateTimeSlot(slot: TimeSlot) = timeSlotRepository.updateTimeSlot(slot)

    suspend fun deleteTimeSlot(slot: TimeSlot) = timeSlotRepository.deleteTimeSlot(slot)

    suspend fun moveTimeSlot(schemeId: Long, fromIndex: Int, toIndex: Int) =
        timeSlotRepository.moveTimeSlot(schemeId, fromIndex, toIndex)
}