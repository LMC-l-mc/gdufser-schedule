package com.gdufs.schedule.ui.screen.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gdufs.schedule.R
import com.gdufs.schedule.data.model.AppSettings
import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.domain.schedule.ScheduleCalculator
import com.gdufs.schedule.domain.schedule.ScheduleQuery
import com.gdufs.schedule.domain.schedule.TermWeek
import com.gdufs.schedule.ui.component.ContactMeButton
import com.gdufs.schedule.ui.component.CourseCard
import com.gdufs.schedule.ui.component.CourseRemarkEditor
import com.gdufs.schedule.ui.screen.settings.AppearanceViewModel
import com.gdufs.schedule.ui.screen.settings.CourseViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.koin.androidx.compose.koinViewModel

/** 时间栏宽度 */
private val TIME_COLUMN_WIDTH = 60.dp

/** 日期栏显示格式,例如"2/23" */
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

/**
 * 周课表页面:
 * - 顶部为课表名称、当前周次与上一周/下一周/周次选择;
 * - 日期栏为周一至周日及具体日期,当前日期所在列高亮;
 * - 左侧时间栏列出作息方案的大节与准确时间;
 * - 主体网格按星期列与大节行放置课程,跨大节跨行显示,重叠课程分栏显示;
 * - 课程卡片复用 [CourseCard],点击打开详情,备注按钮就地编辑;
 * - 处理无课表、无课程、学期未开始/已结束与周次越界状态。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimetableScreen(
    contentPadding: PaddingValues,
    onAddCourse: () -> Unit,
    onOpenCourse: (Long) -> Unit,
) {
    val viewModel: CourseViewModel = koinViewModel()
    val table by viewModel.currentTable.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val slots by viewModel.timeSlots.collectAsState()
    val weekdayNames = stringArrayResource(R.array.weekday_names)
    val appearanceViewModel: AppearanceViewModel = koinViewModel()
    val appearance by appearanceViewModel.settings.collectAsState(initial = AppSettings())

    // 周次状态:课表可用后初始化为"今天"所在周(学期未开始=1,已结束=最后一周)
    var week by rememberSaveable { mutableStateOf(0) }
    var initializedTableId by rememberSaveable { mutableStateOf(-1L) }
    LaunchedEffect(table?.id) {
        val current = table ?: return@LaunchedEffect
        if (initializedTableId != current.id) {
            val todayEpoch = ScheduleCalculator.toEpochMillis(LocalDate.now())
            week = when (
                val term = ScheduleCalculator.weekOfTerm(
                    todayEpoch,
                    current.termStartDate,
                    current.firstDayOfWeek,
                    current.totalWeeks,
                )
            ) {
                is TermWeek.InTerm -> term.week
                TermWeek.NotStarted -> 1
                TermWeek.Ended -> current.totalWeeks
            }
            initializedTableId = current.id
        }
    }

    var showWeekPicker by remember { mutableStateOf(false) }
    var remarkCourse by remember { mutableStateOf<Course?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        val current = table
        if (current == null) {
            CenteredHint(text = stringResource(R.string.course_no_current_table_hint))
            return@Box
        }

        val totalWeeks = current.totalWeeks
        val weekValid = week in 1..totalWeeks
        val todayEpoch = remember(current) { ScheduleCalculator.toEpochMillis(LocalDate.now()) }
        val todayWeek = ScheduleCalculator.weekNumberOf(
            todayEpoch,
            current.termStartDate,
            current.firstDayOfWeek,
        )
        val todayWeekday = ScheduleCalculator.weekdayOf(todayEpoch)
        val todayInView = weekValid && todayWeek == week

        val entries = remember(courses, current, week) {
            if (!weekValid) {
                emptyList()
            } else {
                ScheduleQuery.coursesInWeek(
                    courses,
                    week,
                    current.termStartDate,
                    current.firstDayOfWeek,
                )
            }
        }
        val perDay = remember(entries, slots) {
            if (slots.isEmpty()) {
                emptyMap()
            } else {
                val firstSlot = slots.first().slotNumber
                (1..7).associateWith { day ->
                    layoutDayColumn(entries.filter { it.weekday == day }, firstSlot, slots.size)
                }
            }
        }
        val dates = remember(current, week) {
            if (!weekValid) {
                List(7) { "" }
            } else {
                (1..7).map { day ->
                    val millis = ScheduleCalculator.dateOfWeek(
                        week,
                        day,
                        current.termStartDate,
                        current.firstDayOfWeek,
                    )
                    DATE_FORMATTER.format(ScheduleCalculator.toLocalDate(millis))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // 课表名称(顶部最右为"联系我"按钮)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = current.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                ContactMeButton()
            }

            // 上一周 / 周次选择 / 下一周
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { week = (week - 1).coerceAtLeast(1) },
                    enabled = weekValid && week > 1,
                ) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = stringResource(R.string.timetable_prev_week),
                    )
                }
                TextButton(
                    onClick = { showWeekPicker = true },
                    enabled = weekValid,
                ) {
                    Text(
                        text = stringResource(R.string.course_week_item_format, week.coerceAtLeast(1)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                IconButton(
                    onClick = { week = (week + 1).coerceAtMost(totalWeeks) },
                    enabled = weekValid && week < totalWeeks,
                ) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = stringResource(R.string.timetable_next_week),
                    )
                }
            }

            // 学期状态横幅
            when (
                ScheduleCalculator.weekOfTerm(
                    todayEpoch,
                    current.termStartDate,
                    current.firstDayOfWeek,
                    current.totalWeeks,
                )
            ) {
                TermWeek.NotStarted -> TermBanner(stringResource(R.string.timetable_term_not_started))
                TermWeek.Ended -> TermBanner(stringResource(R.string.timetable_term_ended))
                is TermWeek.InTerm -> Unit
            }
            if (week != 0 && !weekValid) {
                TermBanner(stringResource(R.string.timetable_week_out_of_range))
            }

            when {
                slots.isEmpty() -> {
                    CenteredHint(text = stringResource(R.string.course_no_time_slots_hint))
                }

                courses.isEmpty() -> {
                    CenteredHint(text = stringResource(R.string.course_none_hint))
                }

                else -> {
                    // 日期栏:周一至周日 + 具体日期,今天所在列高亮
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.width(TIME_COLUMN_WIDTH))
                        (1..7).forEach { day ->
                            val highlighted = todayInView && day == todayWeekday
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (highlighted) {
                                            Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = weekdayNames[day - 1],
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (highlighted) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Text(
                                    text = dates.getOrElse(day - 1) { "" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (highlighted) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }

                    // 网格主体:时间栏 + 七列日视图
                    val rowHeight = appearance.timetableRowHeight.dp
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TimeLabelColumn(
                            slots = slots,
                            rowHeight = rowHeight,
                        )
                        (1..7).forEach { day ->
                            DayColumn(
                                placed = perDay[day].orEmpty(),
                                slots = slots,
                                rowHeight = rowHeight,
                                totalRows = slots.size,
                                cardSpacing = appearance.cardSpacing.dp,
                                appearance = appearance,
                                highlighted = todayInView && day == todayWeekday,
                                onCourseClick = onOpenCourse,
                                onRemarkClick = { remarkCourse = it },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        if (slots.isNotEmpty()) {
            FloatingActionButton(
                onClick = onAddCourse,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.course_new),
                )
            }
        }
    }

    if (showWeekPicker && table != null) {
        WeekPickerDialog(
            selectedWeek = week,
            totalWeeks = requireNotNull(table).totalWeeks,
            onSelect = { selected ->
                week = selected
                showWeekPicker = false
            },
            onDismiss = { showWeekPicker = false },
        )
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

/**
 * 星期一列:BoxWithConstraints 内按行高与车道绝对定位课程卡片,
 * 跨大节卡片高度 = 行高 × 跨行数;今天所在列染浅色背景。
 * 卡片外观(圆角/透明度/显示开关)来自用户设置;
 * 卡片间距以卡片四周留 [cardSpacing]/2 边距近似实现。
 */
@Composable
private fun DayColumn(
    placed: List<PlacedCourse>,
    slots: List<TimeSlot>,
    rowHeight: Dp,
    totalRows: Int,
    cardSpacing: Dp,
    appearance: AppSettings,
    highlighted: Boolean,
    onCourseClick: (Long) -> Unit,
    onRemarkClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .height(rowHeight * totalRows)
            .then(
                if (highlighted) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                } else {
                    Modifier
                }
            ),
    ) {
        val columnWidth = maxWidth
        placed.forEach { placedCourse ->
            val cardWidth = columnWidth / placedCourse.laneCount
            val marginPx = with(density) { (cardSpacing / 2).roundToPx() }
            val xOffset = with(density) { (cardWidth * placedCourse.lane).roundToPx() } + marginPx
            val yOffset = with(density) { (rowHeight * placedCourse.startRow).roundToPx() } + marginPx
            val cardWidthDp = (cardWidth - cardSpacing).coerceAtLeast(24.dp)
            val cardHeightDp = (rowHeight * placedCourse.rowSpan - cardSpacing).coerceAtLeast(24.dp)
            CourseCard(
                course = placedCourse.entry.course,
                occurrence = placedCourse.entry.occurrence,
                timeSlots = slots,
                onRemarkClick = { onRemarkClick(placedCourse.entry.course) },
                modifier = Modifier
                    .offset { IntOffset(xOffset, yOffset) }
                    .width(cardWidthDp)
                    .height(cardHeightDp)
                    .clickable { onCourseClick(placedCourse.entry.course.id) },
                compact = true,
                showTeacher = appearance.showTeacher,
                showLocation = appearance.showLocation,
                showTime = appearance.showTime,
                cornerRadius = appearance.cardCornerRadius.dp,
                containerAlpha = appearance.cardOpacity / 100f,
            )
        }
    }
}

/** 左侧时间栏:每行显示大节名称与准确起止时间,与网格行高对齐 */
@Composable
private fun TimeLabelColumn(
    slots: List<TimeSlot>,
    rowHeight: Dp,
) {
    Column(
        modifier = Modifier
            .width(TIME_COLUMN_WIDTH)
            .height(rowHeight * slots.size),
    ) {
        slots.forEach { slot ->
            Column(
                modifier = Modifier
                    .height(rowHeight)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = slot.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                Text(
                    text = "${slot.startTime}–${slot.endTime}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** 学期状态横幅 */
@Composable
private fun TermBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
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

/** 周次选择对话框:芯片即点即选并关闭 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekPickerDialog(
    selectedWeek: Int,
    totalWeeks: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timetable_week_picker_title)) },
        text = {
            FlowRow(
                modifier = Modifier
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                (1..totalWeeks).forEach { candidate ->
                    FilterChip(
                        selected = candidate == selectedWeek,
                        onClick = { onSelect(candidate) },
                        label = {
                            Text(stringResource(R.string.course_week_item_format, candidate))
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}