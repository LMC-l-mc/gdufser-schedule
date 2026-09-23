package com.gdufs.schedule.data.repository

import com.gdufs.schedule.data.db.BaseDatabaseTest
import com.gdufs.schedule.data.db.DefaultSchedule
import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.data.model.TimeSlotScheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 作息方案 Repository 测试:新建/复制/删除保护、时间段增删改与排序。
 */
class TimeSlotRepositoryTest : BaseDatabaseTest() {

    private val repository: TimeSlotRepository by lazy {
        DefaultTimeSlotRepository(database, schemeDao, slotDao, courseTableDao)
    }

    /** 创建含默认七个大节的方案 */
    private fun seedDefaultScheme(name: String = "我的作息"): Long = runBlocking {
        val id = schemeDao.insert(TimeSlotScheme(name = name))
        slotDao.insertAll(DefaultSchedule.defaultSlotsFor(id))
        id
    }

    private fun seedSchemeWithoutSlots(name: String = "空方案"): Long = runBlocking {
        schemeDao.insert(TimeSlotScheme(name = name))
    }

    private fun seedTableUsing(schemeId: Long): Long = runBlocking {
        courseTableDao.insert(
            CourseTable(
                name = "引用该方案的课表",
                termStartDate = 1_739_836_800_000L,
                totalWeeks = 16,
                firstDayOfWeek = 1,
                timeSlotSchemeId = schemeId,
                createdAt = 1L,
                updatedAt = 1L,
            )
        )
    }

    // ---------- 方案新建 / 复制 ----------

    @Test
    fun `新建作息方案`() = runBlocking {
        val id = repository.createScheme("实验课作息")
        assertEquals("实验课作息", schemeDao.getByIdOnce(id)?.name)
    }

    @Test
    fun `复制方案深拷贝全部时间段`() = runBlocking {
        val sourceId = seedDefaultScheme()
        val duplicateId = repository.duplicateScheme(sourceId, "我的作息 副本")

        assertEquals(7, slotDao.getBySchemeOnce(sourceId).size)
        assertEquals(7, slotDao.getBySchemeOnce(duplicateId).size)

        val sourceSlots = slotDao.getBySchemeOnce(sourceId)
        val duplicateSlots = slotDao.getBySchemeOnce(duplicateId)
        sourceSlots.zip(duplicateSlots).forEach { (s, d) ->
            assertNotEquals(s.id, d.id)
            assertEquals(s.slotNumber, d.slotNumber)
            assertEquals(s.displayName, d.displayName)
            assertEquals(s.startTime, d.startTime)
            assertEquals(s.endTime, d.endTime)
            assertEquals(duplicateId, d.schemeId)
        }
        assertEquals("我的作息 副本", schemeDao.getByIdOnce(duplicateId)?.name)
    }

    // ---------- 删除保护 ----------

    @Test
    fun `未被课表使用的方案可以删除且级联删除时间段`() = runBlocking {
        val schemeId = seedDefaultScheme()
        assertEquals(SchemeDeleteResult.Deleted, repository.deleteScheme(schemeId))
        assertEquals(null, schemeDao.getByIdOnce(schemeId))
        assertTrue(slotDao.getBySchemeOnce(schemeId).isEmpty())
    }

    @Test
    fun `被课表使用的方案拒绝删除`() = runBlocking {
        val schemeId = seedDefaultScheme()
        seedTableUsing(schemeId)

        assertEquals(SchemeDeleteResult.InUse, repository.deleteScheme(schemeId))

        // 方案与其时间段原样保留
        assertEquals("我的作息", schemeDao.getByIdOnce(schemeId)?.name)
        assertEquals(7, slotDao.getBySchemeOnce(schemeId).size)
    }

    @Test
    fun `删除不存在的方案视为已删除`() = runBlocking {
        assertEquals(SchemeDeleteResult.Deleted, repository.deleteScheme(999L))
    }

    // ---------- 时间段增删改 ----------

    @Test
    fun `追加时间段自动分配下一大节序号`() = runBlocking {
        val schemeId = seedDefaultScheme()
        val newId = repository.addTimeSlot(schemeId, "第八大节", "21:30", "22:10")
        val slots = slotDao.getBySchemeOnce(schemeId)
        assertEquals(8, slots.size)
        assertEquals(8, slots.last().slotNumber)
        assertEquals("21:30", slots.last().startTime)
        assertEquals(newId, slots.last().id)
    }

    @Test
    fun `空方案追加时间段从第一大节开始`() = runBlocking {
        val schemeId = seedSchemeWithoutSlots()
        repository.addTimeSlot(schemeId, "第一大节", "08:30", "09:50")
        val slots = slotDao.getBySchemeOnce(schemeId)
        assertEquals(1, slots.single().slotNumber)
    }

    @Test
    fun `编辑时间段更新时间与名称`() = runBlocking {
        val schemeId = seedDefaultScheme()
        val slot = slotDao.getBySchemeOnce(schemeId).first()
        repository.updateTimeSlot(slot.copy(displayName = "晨读", startTime = "07:30", endTime = "08:20"))

        val updated = slotDao.getBySchemeOnce(schemeId).first()
        assertEquals("晨读", updated.displayName)
        assertEquals("07:30", updated.startTime)
        assertEquals("08:20", updated.endTime)
        assertEquals(1, updated.slotNumber) // 序号不变
    }

    @Test
    fun `删除中间时间段后序号重新连续编号且默认名跟随更新`() = runBlocking {
        val schemeId = seedDefaultScheme()
        val slots = slotDao.getBySchemeOnce(schemeId)
        val removed = slots[2] // 第三大节 12:30-13:50

        repository.deleteTimeSlot(removed)

        val remaining = slotDao.getBySchemeOnce(schemeId)
        assertEquals(6, remaining.size)
        // 原第四大节(14:00)变为第三大节,时间内容保留
        assertEquals("14:00", remaining[2].startTime)
        assertEquals("15:20", remaining[2].endTime)
        assertEquals(3, remaining[2].slotNumber)
        assertEquals("第三大节", remaining[2].displayName)
    }

    // ---------- 排序 ----------

    @Test
    fun `上移时间段重写序号并同步默认名称`() = runBlocking {
        val schemeId = seedDefaultScheme()

        repository.moveTimeSlot(schemeId, fromIndex = 3, toIndex = 0) // 第四大节提到最前

        val after = slotDao.getBySchemeOnce(schemeId)
        assertEquals(7, after.size)
        // 原第四大节(14:00-15:20)成为第一大节,名称随序号更新
        assertEquals("14:00", after[0].startTime)
        assertEquals(1, after[0].slotNumber)
        assertEquals("第一大节", after[0].displayName)
        // 其余时段依次顺延:原第一大节现在排第二,默认名重写为"第二大节"
        assertEquals("第二大节", after[1].displayName)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), after.map { it.slotNumber })
        // 顺序:原4,1,2,3,5,6,7
        assertEquals(listOf("14:00", "08:30", "10:10", "12:30", "15:40", "18:30", "20:00"), after.map { it.startTime })
    }

    @Test
    fun `自定义名称在排序后保持不变`() = runBlocking {
        val schemeId = seedDefaultScheme()
        val slots = slotDao.getBySchemeOnce(schemeId)
        repository.updateTimeSlot(slots[1].copy(displayName = "数学课"))

        repository.moveTimeSlot(schemeId, fromIndex = 1, toIndex = 5)

        val after = slotDao.getBySchemeOnce(schemeId)
        // 数学课名称保留,序号变为 6
        assertEquals("数学课", after[5].displayName)
        assertEquals(6, after[5].slotNumber)
        // 其余默认名随新序号重写
        assertEquals("第一大节", after[0].displayName)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), after.map { it.slotNumber })
    }

    @Test
    fun `非法移动范围不改变数据`() = runBlocking {
        val schemeId = seedDefaultScheme()
        val before = slotDao.getBySchemeOnce(schemeId)

        repository.moveTimeSlot(schemeId, fromIndex = -1, toIndex = 0)
        repository.moveTimeSlot(schemeId, fromIndex = 0, toIndex = 99)
        repository.moveTimeSlot(schemeId, fromIndex = 2, toIndex = 2)

        val after = slotDao.getBySchemeOnce(schemeId)
        assertEquals(before.map { it.id }, after.map { it.id })
        assertEquals(before.map { it.slotNumber }, after.map { it.slotNumber })
    }
}