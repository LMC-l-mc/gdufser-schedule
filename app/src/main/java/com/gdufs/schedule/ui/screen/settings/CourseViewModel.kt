package com.gdufs.schedule.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.data.model.CourseWithOccurrences
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.data.repository.CourseRepository
import com.gdufs.schedule.data.repository.CourseTableRepository
import com.gdufs.schedule.data.repository.TimeSlotRepository
import com.gdufs.schedule.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 课程管理 ViewModel:
 * - 暴露当前课表及其课程列表、所属作息方案的时间段;
 * - 所有数据库操作经 Repository,写操作在本 ViewModelScope 协程中执行。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CourseViewModel(
    private val courseRepository: CourseRepository,
    courseTableRepository: CourseTableRepository,
    private val timeSlotRepository: TimeSlotRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val courseTables: StateFlow<List<CourseTable>> =
        courseTableRepository.observeCourseTables()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentTableId: StateFlow<Long?> =
        settingsRepository.currentCourseTableId
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 当前选中的课表,未选择时为 null */
    val currentTable: StateFlow<CourseTable?> =
        combine(currentTableId, courseTables) { id, tables ->
            tables.firstOrNull { it.id == id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 当前课表下全部课程(含上课安排) */
    val courses: StateFlow<List<CourseWithOccurrences>> =
        currentTableId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else courseRepository.observeCourses(id)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前课表作息方案的时间段(供大节选择器使用) */
    val timeSlots: StateFlow<List<TimeSlot>> =
        currentTable
            .flatMapLatest { table ->
                if (table == null) {
                    flowOf(emptyList())
                } else {
                    timeSlotRepository.observeTimeSlots(table.timeSlotSchemeId)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun addCourse(
        courseTableId: Long,
        name: String,
        teacher: String,
        location: String,
        remark: String,
        colorArgb: Int,
    ): Long = courseRepository.addCourse(courseTableId, name, teacher, location, remark, colorArgb)

    suspend fun updateCourse(course: Course) = courseRepository.updateCourse(course)

    /** 更新课程备注(空文本即清除备注),写库后经 Room Flow 自动刷新课程卡片 */
    fun updateCourseRemark(course: Course, remark: String) {
        viewModelScope.launch {
            courseRepository.updateCourse(course.copy(remark = remark))
        }
    }

    fun deleteCourse(course: Course) {
        viewModelScope.launch { courseRepository.deleteCourse(course) }
    }

    suspend fun replaceOccurrences(courseId: Long, occurrences: List<CourseOccurrence>) =
        courseRepository.replaceOccurrences(courseId, occurrences)

    suspend fun duplicateCourse(courseId: Long, newName: String): Long =
        courseRepository.duplicateCourse(courseId, newName)
}