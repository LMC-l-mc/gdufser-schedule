package com.gdufs.schedule.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdufs.schedule.R
import com.gdufs.schedule.ui.component.ContactMeButton
import org.koin.androidx.compose.koinViewModel

/** 设置页内部目的地 */
private enum class SettingsPage {
    Home, CourseTables, CourseTableEdit, Schemes, TimeSlotEdit, Courses, CourseEdit, CourseDetail,
    Appearance, Backup, About,
}

/**
 * 设置页:首页提供“课表管理”与“作息时间管理”入口,
 * 子页面在本页内部切换(不占用全局导航栈)。
 * 首次启动且无课表时,首页显示创建第一个课表的引导。
 */
@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    var pageName by rememberSaveable { mutableStateOf(SettingsPage.Home.name) }
    val page = SettingsPage.valueOf(pageName)

    // 编辑目标:tableId/ schemeId/ courseId 为 null 时表示新建
    var editingTableId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingSchemeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingSchemeName by rememberSaveable { mutableStateOf("") }
    var editingCourseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailCourseId by rememberSaveable { mutableStateOf<Long?>(null) }

    BackHandler(enabled = page != SettingsPage.Home) {
        pageName = when (page) {
            SettingsPage.CourseTables, SettingsPage.Schemes, SettingsPage.Courses,
            SettingsPage.Appearance, SettingsPage.Backup, SettingsPage.About ->
                SettingsPage.Home.name
            SettingsPage.CourseTableEdit -> SettingsPage.CourseTables.name
            SettingsPage.TimeSlotEdit -> SettingsPage.Schemes.name
            SettingsPage.CourseEdit -> SettingsPage.Courses.name
            SettingsPage.CourseDetail -> SettingsPage.Courses.name
            SettingsPage.Home -> SettingsPage.Home.name
        }
    }

    when (page) {
        SettingsPage.Home -> SettingsHomeScreen(
            contentPadding = contentPadding,
            onOpenCourseTables = { pageName = SettingsPage.CourseTables.name },
            onOpenSchemes = { pageName = SettingsPage.Schemes.name },
            onOpenCourses = { pageName = SettingsPage.Courses.name },
            onOpenAppearance = { pageName = SettingsPage.Appearance.name },
            onOpenBackup = { pageName = SettingsPage.Backup.name },
            onOpenAbout = { pageName = SettingsPage.About.name },
            onCreateFirstCourseTable = {
                editingTableId = null
                pageName = SettingsPage.CourseTableEdit.name
            },
        )

        SettingsPage.Appearance -> AppearanceScreen(
            contentPadding = contentPadding,
            onBack = { pageName = SettingsPage.Home.name },
        )

        SettingsPage.Backup -> BackupScreen(
            contentPadding = contentPadding,
            onBack = { pageName = SettingsPage.Home.name },
        )

        SettingsPage.About -> AboutScreen(
            contentPadding = contentPadding,
            onBack = { pageName = SettingsPage.Home.name },
        )

        SettingsPage.CourseTables -> CourseTableManageScreen(
            contentPadding = contentPadding,
            onBack = { pageName = SettingsPage.Home.name },
            onEdit = { tableId ->
                editingTableId = tableId
                pageName = SettingsPage.CourseTableEdit.name
            },
        )

        SettingsPage.CourseTableEdit -> CourseTableEditScreen(
            contentPadding = contentPadding,
            onBack = { pageName = SettingsPage.CourseTables.name },
            tableId = editingTableId,
            onDone = { pageName = SettingsPage.CourseTables.name },
        )

        SettingsPage.Schemes -> TimeSlotSchemeManageScreen(
            contentPadding = contentPadding,
            onBack = { pageName = SettingsPage.Home.name },
            onOpenTimeSlots = { schemeId, schemeName ->
                editingSchemeId = schemeId
                editingSchemeName = schemeName
                pageName = SettingsPage.TimeSlotEdit.name
            },
        )

        SettingsPage.TimeSlotEdit -> {
            val schemeId = editingSchemeId
            if (schemeId != null) {
                TimeSlotEditScreen(
                    contentPadding = contentPadding,
                    onBack = { pageName = SettingsPage.Schemes.name },
                    schemeId = schemeId,
                    schemeName = editingSchemeName,
                )
            } else {
                pageName = SettingsPage.Schemes.name
            }
        }

        SettingsPage.Courses -> CourseManageScreen(
            contentPadding = contentPadding,
            onBack = { pageName = SettingsPage.Home.name },
            onEdit = { courseId ->
                editingCourseId = courseId
                pageName = SettingsPage.CourseEdit.name
            },
            onOpenDetail = { courseId ->
                detailCourseId = courseId
                pageName = SettingsPage.CourseDetail.name
            },
        )

        SettingsPage.CourseEdit -> CourseEditScreen(
            contentPadding = contentPadding,
            onBack = { pageName = SettingsPage.Courses.name },
            courseId = editingCourseId,
            onDone = { pageName = SettingsPage.Courses.name },
        )

        SettingsPage.CourseDetail -> {
            val courseId = detailCourseId
            if (courseId != null) {
                CourseDetailScreen(
                    contentPadding = contentPadding,
                    onBack = { pageName = SettingsPage.Courses.name },
                    courseId = courseId,
                    onEdit = {
                        editingCourseId = courseId
                        pageName = SettingsPage.CourseEdit.name
                    },
                    onDeleted = { pageName = SettingsPage.Courses.name },
                )
            } else {
                pageName = SettingsPage.Courses.name
            }
        }
    }
}

/** 设置首页:入口列表 + 首次启动无课表引导 */
@Composable
private fun SettingsHomeScreen(
    contentPadding: PaddingValues,
    onOpenCourseTables: () -> Unit,
    onOpenSchemes: () -> Unit,
    onOpenCourses: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit,
    onCreateFirstCourseTable: () -> Unit,
) {
    val viewModel: CourseTableViewModel = koinViewModel()
    val tables by viewModel.courseTables.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            // 字体放大与横屏时保证全部入口可达
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            // 顶部最右:"联系我"按钮
            ContactMeButton()
        }

        if (tables.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_no_course_table_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onCreateFirstCourseTable) {
                        Text(text = stringResource(R.string.settings_create_first_course_table))
                    }
                }
            }
        }

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_course_tables)) },
            supportingContent = { Text(stringResource(R.string.settings_course_tables_desc)) },
            modifier = Modifier.clickable(onClick = onOpenCourseTables),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_courses)) },
            supportingContent = { Text(stringResource(R.string.settings_courses_desc)) },
            modifier = Modifier.clickable(onClick = onOpenCourses),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_time_slots)) },
            supportingContent = { Text(stringResource(R.string.settings_time_slots_desc)) },
            modifier = Modifier.clickable(onClick = onOpenSchemes),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_appearance)) },
            supportingContent = { Text(stringResource(R.string.settings_appearance_desc)) },
            modifier = Modifier.clickable(onClick = onOpenAppearance),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_backup)) },
            supportingContent = { Text(stringResource(R.string.settings_backup_desc)) },
            modifier = Modifier.clickable(onClick = onOpenBackup),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about)) },
            supportingContent = { Text(stringResource(R.string.settings_about_desc)) },
            modifier = Modifier.clickable(onClick = onOpenAbout),
        )
    }
}