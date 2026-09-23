package com.gdufs.schedule.data.db

import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.data.model.TimeSlotScheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * 时间段排序测试:
 * 无论插入顺序如何,observeByScheme 都必须按大节序号升序返回。
 */
class TimeSlotDaoTest : BaseDatabaseTest() {

    @Test
    fun observeByScheme_returnsSlotsOrderedBySlotNumber() = runBlocking {
        val schemeId = schemeDao.insert(TimeSlotScheme(name = "乱序方案"))

        val slotsOutOfOrder = listOf(
            slot(schemeId, 7, "第七大节", "20:00", "21:20"),
            slot(schemeId, 1, "第一大节", "08:30", "09:50"),
            slot(schemeId, 5, "第五大节", "15:40", "17:00"),
            slot(schemeId, 2, "第二大节", "10:10", "12:15"),
            slot(schemeId, 6, "第六大节", "18:30", "19:50"),
            slot(schemeId, 3, "第三大节", "12:30", "13:50"),
            slot(schemeId, 4, "第四大节", "14:00", "15:20"),
        ).shuffled(Random(42))
        slotDao.insertAll(slotsOutOfOrder)

        val slots = slotDao.observeByScheme(schemeId).first()
        assertEquals(7, slots.size)
        assertEquals((1..7).toList(), slots.map { it.slotNumber })
    }

    @Test
    fun observeByScheme_onlyReturnsSlotsOfGivenScheme() = runBlocking {
        val schemeA = schemeDao.insert(TimeSlotScheme(name = "方案A"))
        val schemeB = schemeDao.insert(TimeSlotScheme(name = "方案B"))
        slotDao.insertAll(listOf(slot(schemeA, 1, "第一大节", "08:30", "09:50")))
        slotDao.insertAll(
            listOf(
                slot(schemeB, 1, "第一大节", "08:00", "09:00"),
                slot(schemeB, 2, "第二大节", "09:10", "10:10"),
            ),
        )

        val slotsA = slotDao.observeByScheme(schemeA).first()
        assertEquals(1, slotsA.size)
        assertEquals("08:30", slotsA.single().startTime)
    }

    private fun slot(
        schemeId: Long,
        slotNumber: Int,
        displayName: String,
        startTime: String,
        endTime: String,
    ) = TimeSlot(
        schemeId = schemeId,
        slotNumber = slotNumber,
        displayName = displayName,
        startTime = startTime,
        endTime = endTime,
    )
}