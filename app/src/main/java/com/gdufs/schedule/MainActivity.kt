package com.gdufs.schedule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gdufs.schedule.ui.ScheduleApp
import com.gdufs.schedule.ui.widget.TodayWidgetProvider

class MainActivity : ComponentActivity() {

    /** 小组件点击带来的课程 id;null 表示仅打开应用 */
    private var startCourseId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startCourseId = readCourseId(intent)
        setContent {
            ScheduleApp(startCourseId = startCourseId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startCourseId = readCourseId(intent)
    }

    private fun readCourseId(intent: Intent?): Long? =
        intent?.getLongExtra(TodayWidgetProvider.EXTRA_COURSE_ID, -1L)?.takeIf { it > 0 }
}