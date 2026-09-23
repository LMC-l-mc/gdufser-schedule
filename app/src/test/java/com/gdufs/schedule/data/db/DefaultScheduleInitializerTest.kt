package com.gdufs.schedule.data.db

import com.gdufs.schedule.data.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 默认数据初始化测试:
 * 首次初始化必须创建"我的作息"方案、七个默认时间段与默认设置行,且重复执行幂等。
 */
class DefaultScheduleInitializerTest : BaseDatabaseTest() {

    @Test
    fun initialize_createsDefaultSchemeWithSevenSlotsInOrder() = runBlocking {
        initializer().initialize()

        val schemes = schemeDao.observeAll().first()
        assertEquals(1, schemes.size)
        assertEquals(DefaultSchedule.DEFAULT_SCHEME_NAME, schemes.single().name)

        val slots = slotDao.observeByScheme(schemes.single().id).first()
        assertEquals(DefaultSchedule.DEFAULT_SLOT_SPECS.size, slots.size)
        // 时间段必须按大节序号升序
        assertEquals((1..7).toList(), slots.map { it.slotNumber })
        DefaultSchedule.DEFAULT_SLOT_SPECS.forEachIndexed { index, spec ->
            assertEquals(spec.displayName, slots[index].displayName)
            assertEquals(spec.startTime, slots[index].startTime)
            assertEquals(spec.endTime, slots[index].endTime)
        }
    }

    @Test
    fun initialize_isIdempotent() = runBlocking {
        initializer().initialize()
        initializer().initialize()

        assertEquals(1, schemeDao.count())
        val schemeId = schemeDao.observeAll().first().single().id
        assertEquals(
            DefaultSchedule.DEFAULT_SLOT_SPECS.size,
            slotDao.observeByScheme(schemeId).first().size,
        )
    }

    @Test
    fun initialize_createsDefaultSettingsRow() = runBlocking {
        initializer().initialize()

        val settings = settingsDao.observe().first()
        assertNotNull(settings)
        assertEquals(ThemeMode.SYSTEM, settings!!.themeMode)
        assertEquals(null, settings.currentCourseTableId)
    }
}