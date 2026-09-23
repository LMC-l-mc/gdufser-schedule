package com.gdufs.schedule.ui.screen.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdufs.schedule.R
import com.gdufs.schedule.data.model.AppSettings
import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.domain.schedule.DayCourseEntry
import com.gdufs.schedule.domain.schedule.ScheduleCalculator
import com.gdufs.schedule.domain.schedule.ScheduleQuery
import com.gdufs.schedule.domain.schedule.TermWeek
import com.gdufs.schedule.domain.schedule.TodaySchedule
import com.gdufs.schedule.ui.component.ContactMeButton
import com.gdufs.schedule.ui.component.CourseCard
import com.gdufs.schedule.ui.component.CourseRemarkEditor
import com.gdufs.schedule.ui.screen.settings.AppearanceViewModel
import com.gdufs.schedule.ui.screen.settings.CourseViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import org.koin.androidx.compose.koinViewModel

/** 今日页日期显示格式,例如"9月22日" */
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

/**
 * 今日课程页:
 * - 顶部显示今日日期、星期与当前第几周;
 * - 高亮"正在上课"与"下一节(含开始时间)";
 * - 列出今日全部课程与明日课程预览;
 * - 课程卡片复用 [CourseCard],点击进入课程编辑页,备注按钮就地编辑(写库后 Room Flow 自动刷新);
 * - 无课表、学期未开始/已结束、当天无课程分别显示明确引导。
 */
@Composable
fun TodayScreen(
    contentPadding: PaddingValues,
    onOpenEdit: (Long) -> Unit,
) {
    val viewModel: CourseViewModel = koinViewModel()
    val currentTable by viewModel.currentTable.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val timeSlots by viewModel.timeSlots.collectAsState()
    val weekdayNames = stringArrayResource(R.array.weekday_names)
    val appearanceViewModel: AppearanceViewModel = koinViewModel()
    val appearance by appearanceViewModel.settings.collectAsState(initial = AppSettings())

    var remarkCourse by remember { mutableStateOf<Course?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        val table = currentTable
        if (table == null) {
            CenteredHint(stringResource(R.string.course_no_current_table_hint))
            return@Box
        }

        val today = remember(table) { LocalDate.now() }
        val todayEpoch = remember(today) { ScheduleCalculator.toEpochMillis(today) }

        when (
            ScheduleCalculator.weekOfTerm(
                todayEpoch,
                table.termStartDate,
                table.firstDayOfWeek,
                table.totalWeeks,
            )
        ) {
            TermWeek.NotStarted -> {
                CenteredHint(stringResource(R.string.today_term_not_started_hint))
            }

            TermWeek.Ended -> {
                CenteredHint(stringResource(R.string.today_term_ended_hint))
            }

            is TermWeek.InTerm -> {
                val week = ScheduleCalculator.weekNumberOf(
                    todayEpoch,
                    table.termStartDate,
                    table.firstDayOfWeek,
                )
                val todayEntries = remember(courses, todayEpoch, table) {
                    ScheduleQuery.coursesOnDay(
                        courses,
                        todayEpoch,
                        table.termStartDate,
                        table.firstDayOfWeek,
                        table.totalWeeks,
                    )
                }
                val tomorrow = remember(today) { today.plusDays(1) }
                val tomorrowEpoch = remember(tomorrow) { ScheduleCalculator.toEpochMillis(tomorrow) }
                val tomorrowInTerm = remember(tomorrowEpoch, table) {
                    ScheduleCalculator.weekOfTerm(
                        tomorrowEpoch,
                        table.termStartDate,
                        table.firstDayOfWeek,
                        table.totalWeeks,
                    ) is TermWeek.InTerm
                }
                val tomorrowEntries = remember(courses, tomorrowEpoch, table, tomorrowInTerm) {
                    if (tomorrowInTerm) {
                        ScheduleQuery.coursesOnDay(
                            courses,
                            tomorrowEpoch,
                            table.termStartDate,
                            table.firstDayOfWeek,
                            table.totalWeeks,
                        )
                    } else {
                        emptyList()
                    }
                }

                val now = LocalTime.now()
                val ongoing = remember(todayEntries, timeSlots, now) {
                    TodaySchedule.ongoing(todayEntries, timeSlots, now)
                }
                val nextEntry = remember(todayEntries, timeSlots, now) {
                    TodaySchedule.next(todayEntries, timeSlots, now)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // 今日日期、星期与第几周(顶部最右为"联系我"按钮)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${DATE_FORMATTER.format(today)} ${weekdayNames[today.dayOfWeek.value - 1]}",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                text = stringResource(R.string.course_week_item_format, week),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        ContactMeButton()
                    }

                    ongoing?.let { entry ->
                        SectionLabel(stringResource(R.string.today_now_course))
                        EntryCard(
                            entry = entry,
                            timeSlots = timeSlots,
                            appearance = appearance,
                            onOpenEdit = onOpenEdit,
                            onRemarkClick = { remarkCourse = entry.course },
                        )
                    }

                    nextEntry?.let { entry ->
                        val label = TodaySchedule.startTimeOf(entry, timeSlots)
                            ?.let { stringResource(R.string.today_next_course_with_time_format, it) }
                            ?: stringResource(R.string.today_next_course)
                        SectionLabel(label)
                        EntryCard(
                            entry = entry,
                            timeSlots = timeSlots,
                            appearance = appearance,
                            onOpenEdit = onOpenEdit,
                            onRemarkClick = { remarkCourse = entry.course },
                        )
                    }

                    SectionLabel(stringResource(R.string.today_all_courses))
                    if (todayEntries.isEmpty()) {
                        HintText(stringResource(R.string.today_no_course_hint))
                    } else {
                        todayEntries.forEach { entry ->
                            EntryCard(
                                entry = entry,
                                timeSlots = timeSlots,
                                appearance = appearance,
                                onOpenEdit = onOpenEdit,
                                onRemarkClick = { remarkCourse = entry.course },
                            )
                        }
                    }

                    if (tomorrowInTerm) {
                        SectionLabel(stringResource(R.string.today_tomorrow_courses))
                        if (tomorrowEntries.isEmpty()) {
                            HintText(stringResource(R.string.today_no_course_tomorrow_hint))
                        } else {
                            tomorrowEntries.forEach { entry ->
                                EntryCard(
                                    entry = entry,
                                    timeSlots = timeSlots,
                                    appearance = appearance,
                                    onOpenEdit = onOpenEdit,
                                    onRemarkClick = { remarkCourse = entry.course },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    remarkCourse?.let { course ->
        CourseRemarkEditor(
            course = course,
            initialRemark = course.remark,
            onDismiss = { remarkCourse = null },
            onSave = { remark ->
                viewModel.updateCourseRemark(course, remark)
                remarkCourse = null
            },
        )
    }
}

/** 一条今日/明日课程卡片:点击进入编辑页,备注按钮打开备注编辑;外观来自用户设置 */
@Composable
private fun EntryCard(
    entry: DayCourseEntry,
    timeSlots: List<TimeSlot>,
    appearance: AppSettings,
    onOpenEdit: (Long) -> Unit,
    onRemarkClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = appearance.cardSpacing.dp),
    ) {
        CourseCard(
            course = entry.course,
            occurrence = entry.occurrence,
            timeSlots = timeSlots,
            onRemarkClick = onRemarkClick,
            modifier = Modifier.clickable { onOpenEdit(entry.course.id) },
            showTeacher = appearance.showTeacher,
            showLocation = appearance.showLocation,
            showTime = appearance.showTime,
            cornerRadius = appearance.cardCornerRadius.dp,
            containerAlpha = appearance.cardOpacity / 100f,
            remarkLines = appearance.remarkPreviewLines,
        )
    }
}

/** 小节标题 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 4.dp),
    )
}

/** 正文提示文本 */
@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** 居中提示 */
@Composable
private fun CenteredHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}