package com.gdufs.schedule.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gdufs.schedule.R
import com.gdufs.schedule.ui.screen.settings.CourseDetailScreen
import com.gdufs.schedule.ui.screen.settings.CourseEditScreen
import com.gdufs.schedule.ui.screen.settings.SettingsScreen
import com.gdufs.schedule.ui.screen.timetable.TimetableScreen
import com.gdufs.schedule.ui.screen.today.TodayScreen

/** 全局路由:课程详情(参数课程 id) */
const val COURSE_DETAIL_ROUTE = "course_detail/{courseId}"

/** 全局路由:课程新增/编辑(参数课程 id,-1 表示新增) */
const val COURSE_EDIT_ROUTE = "course_edit/{courseId}"

private const val COURSE_ID_ARG = "courseId"

fun courseDetailRoute(courseId: Long): String = "course_detail/$courseId"

fun courseEditRoute(courseId: Long?): String = "course_edit/${courseId ?: -1L}"

/** 顶级导航目的地:今日 / 课表 / 设置 */
private sealed class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    data object Today : TopLevelDestination("today", R.string.nav_today, Icons.Filled.Today)
    data object Timetable : TopLevelDestination("timetable", R.string.nav_schedule, Icons.Filled.CalendarMonth)
    data object Settings : TopLevelDestination("settings", R.string.nav_settings, Icons.Filled.Settings)
}

private val topLevelDestinations = listOf(
    TopLevelDestination.Today,
    TopLevelDestination.Timetable,
    TopLevelDestination.Settings,
)

@Composable
fun ScheduleNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = currentRoute in topLevelDestinations.map { it.route }

    Scaffold(
        bottomBar = {
            // 详情/编辑等子页面隐藏底部导航栏
            if (isTopLevel) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(text = stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Today.route,
            modifier = Modifier,
        ) {
            composable(TopLevelDestination.Today.route) {
                TodayScreen(
                    contentPadding = innerPadding,
                    onOpenEdit = { courseId -> navController.navigate(courseEditRoute(courseId)) },
                )
            }
            composable(TopLevelDestination.Timetable.route) {
                TimetableScreen(
                    contentPadding = innerPadding,
                    onAddCourse = { navController.navigate(courseEditRoute(null)) },
                    onOpenCourse = { courseId -> navController.navigate(courseDetailRoute(courseId)) },
                )
            }
            composable(TopLevelDestination.Settings.route) {
                SettingsScreen(contentPadding = innerPadding)
            }
            composable(
                route = COURSE_DETAIL_ROUTE,
                arguments = listOf(navArgument(COURSE_ID_ARG) { type = NavType.LongType }),
            ) { entry ->
                val courseId = entry.arguments?.getLong(COURSE_ID_ARG) ?: 0L
                if (courseId > 0) {
                    CourseDetailScreen(
                        contentPadding = innerPadding,
                        onBack = { navController.popBackStack() },
                        courseId = courseId,
                        onEdit = { navController.navigate(courseEditRoute(courseId)) },
                        onDeleted = { navController.popBackStack() },
                    )
                }
            }
            composable(
                route = COURSE_EDIT_ROUTE,
                arguments = listOf(navArgument(COURSE_ID_ARG) { type = NavType.LongType }),
            ) { entry ->
                val rawId = entry.arguments?.getLong(COURSE_ID_ARG) ?: -1L
                CourseEditScreen(
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                    courseId = rawId.takeIf { it > 0 },
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}