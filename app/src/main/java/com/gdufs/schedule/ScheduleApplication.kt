package com.gdufs.schedule

import android.app.Application
import com.gdufs.schedule.data.db.DefaultScheduleInitializer
import com.gdufs.schedule.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.GlobalContext

/**
 * 应用入口:在进程启动时初始化 Koin,并在后台执行默认数据初始化
 * (创建默认作息方案与默认设置,幂等)。
 */
class ScheduleApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ScheduleApplication)
            modules(appModule)
        }
        applicationScope.launch {
            GlobalContext.get().get<DefaultScheduleInitializer>().initialize()
        }
    }
}