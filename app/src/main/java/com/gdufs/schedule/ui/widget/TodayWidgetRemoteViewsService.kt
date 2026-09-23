package com.gdufs.schedule.ui.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.gdufs.schedule.R
import com.gdufs.schedule.data.db.WidgetSnapshotEntity
import com.gdufs.schedule.domain.schedule.ScheduleCalculator
import com.gdufs.schedule.domain.widget.WidgetSnapshotBuilder
import com.gdufs.schedule.domain.widget.WidgetSnapshotItem
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import java.time.LocalDate

/**
 * 小组件列表数据服务:仅为 [TodayWidgetProvider] 提供条目,
 * 数据仅来自快照库,不做任何课表计算。
 */
class TodayWidgetRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TodayWidgetRemoteViewsFactory(applicationContext)
}

private class TodayWidgetRemoteViewsFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<WidgetSnapshotItem> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val snapshotDatabase = GlobalContext.get().get<com.gdufs.schedule.data.db.WidgetSnapshotDatabase>()
        val snapshot = runBlocking { snapshotDatabase.widgetSnapshotDao().getOnce() }
        val today = ScheduleCalculator.toEpochMillis(LocalDate.now())
        items = if (
            snapshot != null &&
            snapshot.status == WidgetSnapshotEntity.STATUS_READY &&
            snapshot.dateEpochMillis == today
        ) {
            try {
                WidgetSnapshotBuilder.fromPayloadJson(snapshot.payloadJson).items
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    override fun onDestroy() = Unit

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items[position]
        val views = RemoteViews(context.packageName, R.layout.widget_today_item)
        views.setTextViewText(R.id.widget_item_name, item.name)
        bindOptional(views, R.id.widget_item_teacher, R.string.widget_item_teacher_format, item.teacher)
        views.setTextViewText(
            R.id.widget_item_time,
            context.getString(R.string.widget_item_time_format, item.slotLabel, item.timeRange),
        )
        bindOptional(views, R.id.widget_item_location, R.string.widget_item_location_format, item.location)
        views.setViewVisibility(R.id.widget_item_has_remark, if (item.hasRemark) View.VISIBLE else View.GONE)

        // 条目点击:填充课程 id,打开应用对应课程详情
        val fillIn = Intent().putExtra(TodayWidgetProvider.EXTRA_COURSE_ID, item.courseId)
        views.setOnClickFillInIntent(R.id.widget_item_root, fillIn)
        return views
    }

    /** 可选文本行:内容为空时整行隐藏 */
    private fun bindOptional(views: RemoteViews, viewId: Int, formatRes: Int, value: String) {
        if (value.isBlank()) {
            views.setViewVisibility(viewId, View.GONE)
        } else {
            views.setViewVisibility(viewId, View.VISIBLE)
            views.setTextViewText(viewId, context.getString(formatRes, value))
        }
    }

    override fun getLoadingView(): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_today_item).apply {
            setTextViewText(R.id.widget_item_name, context.getString(R.string.widget_loading))
        }

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = items[position].courseId

    override fun hasStableIds(): Boolean = true
}