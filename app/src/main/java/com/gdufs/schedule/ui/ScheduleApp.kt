package com.gdufs.schedule.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.gdufs.schedule.data.model.AppSettings
import com.gdufs.schedule.ui.navigation.ScheduleNavHost
import com.gdufs.schedule.ui.screen.settings.AppearanceViewModel
import com.gdufs.schedule.ui.theme.GdufsScheduleTheme
import org.koin.androidx.compose.koinViewModel

/**
 * Compose 应用根组件:
 * 收集主题设置(主题模式与动态颜色)套用主题后直接进入主导航(今日/课表/设置),
 * 设置变化经 Flow 即时全局生效;
 * [startCourseId] 不为空时(如小组件点击)直接导航到对应课程详情。
 * 应用图标(桌面封面)使用"广东外语外贸大学"图片,见 mipmap-anydpi-v26/ic_launcher.xml。
 */
@Composable
fun ScheduleApp(startCourseId: Long? = null) {
    val appearanceViewModel: AppearanceViewModel = koinViewModel()
    val settings by appearanceViewModel.settings.collectAsState(initial = AppSettings())
    GdufsScheduleTheme(
        themeMode = settings.themeMode,
        dynamicColorEnabled = settings.dynamicColorEnabled,
    ) {
        ScheduleNavHost(startCourseId = startCourseId)
    }
}