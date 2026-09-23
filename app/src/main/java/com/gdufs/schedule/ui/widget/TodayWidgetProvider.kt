package com.gdufs.schedule.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.gdufs.schedule.MainActivity
import com.gdufs.schedule.R
import com.gdufs.schedule.data.db.WidgetSnapshotEntity
import com.gdufs.schedule.domain.schedule.ScheduleCalculator
import com.gdufs.schedule.domain.widget.WidgetSnapshotBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.time.LocalDate

/**
 * "今日课程"小组件:仅读取 [com.gdufs.schedule.data.db.WidgetSnapshotDatabase] 快照,
 * 不运行课表计算。展示日期、下一节提示与今日课程列表;
 * 点击列表项/标题打开应用并进入对应课程详情。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    companion object {
        /** 打开应用时携带的课程 id(-1 表示仅打开应用) */
        const val EXTRA_COURSE_ID = "com.gdufs.schedule.extra.COURSE_ID"

        fun openAppIntent(context: Context, courseId: Long): Intent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_COURSE_ID, courseId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        fun openAppPendingIntent(context: Context, courseId: Long): PendingIntent =
            PendingIntent.getActivity(
                context,
                courseId.toInt(),
                openAppIntent(context, courseId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = buildBaseViews(context)
        val listIntent = Intent(context, TodayWidgetRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        views.setRemoteAdapter(R.id.widget_list, listIntent)
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)
        views.setPendingIntentTemplate(R.id.widget_list, openAppPendingIntent(context, -1L))
        manager.updateAppWidget(widgetId, views)
        manager.notifyAppWidgetViewDataChanged(widgetId, R.layout.widget_today)
        refreshHeaderFromSnapshot(context, manager, widgetId)
    }

    /** 基础视图:标题/下一节提示行默认加载中文案,标题区点击打开应用 */
    private fun buildBaseViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title_loading))
        views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_loading))
        views.setViewVisibility(R.id.widget_next, View.GONE)
        views.setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent(context, -1L))
        views.setOnClickPendingIntent(R.id.widget_empty, openAppPendingIntent(context, -1L))
        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context, -1L))
        return views
    }

    /** 异步读取快照,更新标题、下一节与空态文案 */
    private fun refreshHeaderFromSnapshot(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val snapshotDatabase = GlobalContext.get().get<com.gdufs.schedule.data.db.WidgetSnapshotDatabase>()
            val snapshot = snapshotDatabase.widgetSnapshotDao().getOnce()
            val today = ScheduleCalculator.toEpochMillis(LocalDate.now())
            val views = RemoteViews(context.packageName, R.layout.widget_today)

            when {
                snapshot == null || snapshot.dateEpochMillis != today -> {
                    // 快照缺失或已过期 → 加载中
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title_loading))
                    views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_loading))
                    views.setViewVisibility(R.id.widget_next, View.GONE)
                }

                snapshot.status == WidgetSnapshotEntity.STATUS_ERROR -> {
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title_loading))
                    views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_refresh_failed))
                    views.setViewVisibility(R.id.widget_next, View.GONE)
                }

                snapshot.status == WidgetSnapshotEntity.STATUS_EMPTY -> {
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title_loading))
                    views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_no_courses))
                    views.setViewVisibility(R.id.widget_next, View.GONE)
                }

                else -> {
                    val content = WidgetSnapshotBuilder.fromPayloadJson(snapshot.payloadJson)
                    val weekdayName = context.resources
                        .getStringArray(R.array.weekday_names)
                        .getOrElse(content.weekdayIndex - 1) { "" }
                    views.setTextViewText(
                        R.id.widget_title,
                        context.getString(R.string.widget_title_format, content.dateLabel, weekdayName),
                    )
                    val nextIndex = content.nextItemIndex
                    if (nextIndex != null && nextIndex in content.items.indices) {
                        val next = content.items[nextIndex]
                        views.setTextViewText(
                            R.id.widget_next,
                            context.getString(
                                R.string.widget_next_format,
                                next.timeRange.substringBefore("–"),
                                next.name,
                            ),
                        )
                        views.setViewVisibility(R.id.widget_next, View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.widget_next, View.GONE)
                    }
                    views.setViewVisibility(R.id.widget_empty, View.GONE)
                }
            }
            manager.partiallyUpdateAppWidget(widgetId, views)
        }
    }
}