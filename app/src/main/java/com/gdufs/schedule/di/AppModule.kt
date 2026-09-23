package com.gdufs.schedule.di

import com.gdufs.schedule.data.db.AppDatabase
import com.gdufs.schedule.data.db.AppSettingsDao
import com.gdufs.schedule.data.db.CourseDao
import com.gdufs.schedule.data.db.CourseOccurrenceDao
import com.gdufs.schedule.data.db.CourseTableDao
import com.gdufs.schedule.data.db.DefaultScheduleInitializer
import com.gdufs.schedule.data.db.TimeSlotDao
import com.gdufs.schedule.data.db.TimeSlotSchemeDao
import com.gdufs.schedule.data.repository.CourseRepository
import com.gdufs.schedule.data.repository.CourseTableRepository
import com.gdufs.schedule.data.repository.DefaultCourseRepository
import com.gdufs.schedule.data.repository.DefaultCourseTableRepository
import com.gdufs.schedule.data.repository.DefaultTimeSlotRepository
import com.gdufs.schedule.data.repository.BackupRepository
import com.gdufs.schedule.data.repository.DefaultBackupRepository
import com.gdufs.schedule.data.repository.TimeSlotRepository
import com.gdufs.schedule.data.settings.SettingsRepository
import com.gdufs.schedule.data.settings.SettingsRepositoryImpl
import com.gdufs.schedule.ui.screen.settings.AppearanceViewModel
import com.gdufs.schedule.ui.screen.settings.BackupViewModel
import com.gdufs.schedule.ui.screen.settings.CourseTableViewModel
import com.gdufs.schedule.ui.screen.settings.CourseViewModel
import com.gdufs.schedule.ui.screen.settings.TimeSlotSchemeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Android 端依赖注入模块。
 * 后续引入 KMP 共享模块后,此处只保留 Android 专属实现,
 * 通用业务依赖迁移至共享模块的 Koin 模块。
 */
val appModule = module {
    single { AppDatabase.build(androidContext()) }
    single<CourseTableDao> { get<AppDatabase>().courseTableDao() }
    single<TimeSlotSchemeDao> { get<AppDatabase>().timeSlotSchemeDao() }
    single<TimeSlotDao> { get<AppDatabase>().timeSlotDao() }
    single<CourseDao> { get<AppDatabase>().courseDao() }
    single<CourseOccurrenceDao> { get<AppDatabase>().courseOccurrenceDao() }
    single<AppSettingsDao> { get<AppDatabase>().appSettingsDao() }

    single<DefaultScheduleInitializer> {
        DefaultScheduleInitializer(get(), get(), get(), get())
    }

    single<CourseTableRepository> { DefaultCourseTableRepository(get(), get(), get(), get()) }
    single<CourseRepository> { DefaultCourseRepository(get(), get(), get()) }
    single<TimeSlotRepository> { DefaultTimeSlotRepository(get(), get(), get(), get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single<BackupRepository> { DefaultBackupRepository(get()) }

    viewModel { CourseViewModel(get(), get(), get(), get()) }
    viewModel { CourseTableViewModel(get(), get(), get()) }
    viewModel { TimeSlotSchemeViewModel(get(), get()) }
    viewModel { AppearanceViewModel(get()) }
    viewModel { BackupViewModel(get()) }
}