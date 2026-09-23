package com.gdufs.schedule.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.data.model.TimeSlotScheme
import com.gdufs.schedule.data.repository.CourseTableRepository
import com.gdufs.schedule.data.repository.TimeSlotRepository
import com.gdufs.schedule.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 课表管理 ViewModel:
 * - 暴露课表列表、当前课表、作息方案列表(供表单选择);
 * - 所有数据库操作经 Repository,写操作在本 ViewModelScope 协程中执行。
 */
class CourseTableViewModel(
    private val courseTableRepository: CourseTableRepository,
    private val timeSlotRepository: TimeSlotRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val courseTables: StateFlow<List<CourseTable>> =
        courseTableRepository.observeCourseTables()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentTableId: StateFlow<Long?> =
        settingsRepository.currentCourseTableId
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val schemes: StateFlow<List<TimeSlotScheme>> =
        timeSlotRepository.observeSchemes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @Suppress("unused") // 预留给后续页面按 id 订阅
    fun observeCourseTable(id: Long): Flow<CourseTable?> = courseTableRepository.observeCourseTable(id)

    /**
     * 创建课表;若当前没有已选课表(首次启动引导场景),自动切换为新课表。
     * 返回新表 id。
     */
    suspend fun createCourseTable(
        name: String,
        termStartDate: Long,
        totalWeeks: Int,
        firstDayOfWeek: Int,
        timeSlotSchemeId: Long,
    ): Long {
        val newId = courseTableRepository.createCourseTable(
            name = name,
            termStartDate = termStartDate,
            totalWeeks = totalWeeks,
            firstDayOfWeek = firstDayOfWeek,
            timeSlotSchemeId = timeSlotSchemeId,
        )
        if (currentTableId.value == null) {
            settingsRepository.setCurrentCourseTable(newId)
        }
        return newId
    }

    /** 更新课表(但调用方需通过 try 捕获异常) */
    suspend fun updateCourseTable(courseTable: CourseTable) {
        courseTableRepository.updateCourseTable(courseTable)
    }

    /** 删除课表;若被删的是当前课表,清除当前选择 */
    fun deleteCourseTable(courseTable: CourseTable) {
        viewModelScope.launch {
            courseTableRepository.deleteCourseTable(courseTable)
            if (currentTableId.value == courseTable.id) {
                settingsRepository.setCurrentCourseTable(null)
            }
        }
    }

    /** 复制课表(深拷贝课程与安排),返回副本 id */
    suspend fun duplicateCourseTable(courseTableId: Long, newName: String): Long =
        courseTableRepository.duplicateCourseTable(courseTableId, newName)

    /** 切换当前课表 */
    suspend fun switchTo(courseTableId: Long) {
        settingsRepository.setCurrentCourseTable(courseTableId)
    }

    /** 删除前提示所需:课表下课程数量 */
    suspend fun courseCount(courseTableId: Long): Int =
        courseTableRepository.courseCount(courseTableId)
}