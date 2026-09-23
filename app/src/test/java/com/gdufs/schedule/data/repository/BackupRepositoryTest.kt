package com.gdufs.schedule.data.repository

import com.gdufs.schedule.data.db.BaseDatabaseTest
import com.gdufs.schedule.data.model.AppSettings
import com.gdufs.schedule.data.model.Course
import com.gdufs.schedule.data.model.CourseOccurrence
import com.gdufs.schedule.data.model.CourseTable
import com.gdufs.schedule.data.model.TimeSlot
import com.gdufs.schedule.data.model.TimeSlotScheme
import com.gdufs.schedule.domain.backup.BackupData
import com.gdufs.schedule.domain.backup.BackupFormatException
import com.gdufs.schedule.domain.backup.BackupJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * 备份/恢复仓库测试(内存数据库):
 * 验证导出全量、覆盖导入替换数据、新建导入保留现有并重映射 id、
 * 合并导入复用相同作息方案,以及错误文件(解析校验失败)不会破坏已有数据。
 */
class BackupRepositoryTest : BaseDatabaseTest() {

    private lateinit var repository: DefaultBackupRepository

    @Before
    fun setUpRepository() {
        repository = DefaultBackupRepository(database)
    }

    private suspend fun insertTable(
        name: String,
        schemeId: Long,
    ): Long = courseTableDao.insert(
        CourseTable(
            id = 0L,
            name = name,
            termStartDate = 1_752_571_200_000L,
            totalWeeks = 20,
            firstDayOfWeek = 1,
            timeSlotSchemeId = schemeId,
            createdAt = 1L,
            updatedAt = 1L,
        )
    )

    private fun backupData(
        tableName: String = "备份课表",
        schemeName: String = "备份作息",
        courseName: String = "备份课程",
        slots: List<TimeSlot> = listOf(
            TimeSlot(schemeId = 20L, slotNumber = 1, displayName = "第一节", startTime = "08:00", endTime = "09:00"),
        ),
    ): BackupData = BackupData(
        exportedAtEpochMillis = 1L,
        courseTables = listOf(
            CourseTable(
                id = 10L,
                name = tableName,
                termStartDate = 1_752_571_200_000L,
                totalWeeks = 20,
                firstDayOfWeek = 1,
                timeSlotSchemeId = 20L,
                createdAt = 1L,
                updatedAt = 1L,
            )
        ),
        schemes = listOf(TimeSlotScheme(id = 20L, name = schemeName)),
        // 时间段统一挂到备份内的方案 id(20)下,保证备份自洽
        timeSlots = slots.map { it.copy(schemeId = 20L) },
        courses = listOf(Course(id = 40L, courseTableId = 10L, name = courseName)),
        occurrences = listOf(
            CourseOccurrence(id = 50L, courseId = 40L, weekday = 2, startSlot = 1, endSlot = 1),
        ),
        settings = AppSettings(currentCourseTableId = 10L),
    )

    @Test
    fun `导出全量_包含全部实体与设置`() = runBlocking {
        initializer().initialize()
        val scheme = schemeDao.observeAll().first().single()
        val tableId = insertTable("学期", scheme.id)
        val courseId = courseDao.insert(Course(courseTableId = tableId, name = "高数", teacher = "张"))
        occurrenceDao.insertAll(
            listOf(CourseOccurrence(courseId = courseId, weekday = 1, startSlot = 1, endSlot = 2)),
        )
        settingsDao.upsert(AppSettings(currentCourseTableId = tableId))

        val data = repository.exportAll()

        assertEquals(1, data.courseTables.size)
        assertEquals(1, data.schemes.size)
        assertEquals(7, data.timeSlots.size)
        assertEquals(1, data.courses.size)
        assertEquals(1, data.occurrences.size)
        assertEquals(tableId, data.settings.currentCourseTableId)
    }

    @Test
    fun `覆盖导入_清空旧数据并写入备份`() = runBlocking {
        initializer().initialize()
        val scheme = schemeDao.observeAll().first().single()
        val oldTableId = insertTable("旧课表", scheme.id)
        courseDao.insert(Course(courseTableId = oldTableId, name = "旧课程"))
        settingsDao.upsert(AppSettings(currentCourseTableId = oldTableId))

        repository.importOverwrite(backupData())

        val tables = courseTableDao.getAllOnce()
        assertEquals(1, tables.size)
        assertEquals(10L, tables.single().id)
        assertEquals("备份课表", tables.single().name)
        val courses = courseDao.getAllOnce()
        assertEquals(1, courses.size)
        assertEquals("备份课程", courses.single().name)
        assertEquals(1, occurrenceDao.getAllOnce().size)
        assertEquals(10L, settingsDao.getOnce()!!.currentCourseTableId)
        assertNull(courseTableDao.getByIdOnce(oldTableId))
    }

    @Test
    fun `覆盖导入_备份当前课表id不存在时落到第一张课表`() = runBlocking {
        initializer().initialize()
        val scheme = schemeDao.observeAll().first().single()
        insertTable("旧课表", scheme.id)

        val data = backupData().copy(settings = AppSettings(currentCourseTableId = 999L))
        repository.importOverwrite(data)

        assertEquals(10L, settingsDao.getOnce()!!.currentCourseTableId)
    }

    @Test
    fun `新建导入_保留现有数据并重映射id`() = runBlocking {
        initializer().initialize()
        val scheme = schemeDao.observeAll().first().single()
        val oldTableId = insertTable("旧课表", scheme.id)
        settingsDao.upsert(AppSettings(currentCourseTableId = oldTableId))

        repository.importAppend(backupData())

        val tables = courseTableDao.getAllOnce()
        assertEquals(2, tables.size)
        val newTable = tables.first { it.name == "备份课表" }
        // 备份中的旧 id(10/20/30/40/50)被整体重映射
        assertTrue(newTable.id != 10L)
        assertTrue(newTable.timeSlotSchemeId != 20L)
        val newCourses = courseDao.getByTableOnce(newTable.id)
        assertEquals(1, newCourses.size)
        assertEquals("备份课程", newCourses.single().course.name)
        assertEquals(1, newCourses.single().occurrences.size)
        assertEquals(2, newCourses.single().occurrences.single().weekday)
        // 新方案及其时间段一并写入
        assertEquals(1, slotDao.getBySchemeOnce(newTable.timeSlotSchemeId).size)
        // 当前课表与旧数据不变
        assertEquals(oldTableId, settingsDao.getOnce()!!.currentCourseTableId)
        assertEquals("旧课表", courseTableDao.getByIdOnce(oldTableId)!!.name)
    }

    @Test
    fun `合并导入_相同作息方案复用现有`() = runBlocking {
        initializer().initialize()
        val default = schemeDao.observeAll().first().single()
        val defaultSlots = slotDao.getBySchemeOnce(default.id)

        val data = backupData(
            schemeName = default.name,
            slots = defaultSlots,
        )
        repository.importMerge(data)

        // 名称与时间段一致 → 不新建方案
        assertEquals(1, schemeDao.count())
        val newTable = courseTableDao.getAllOnce().first { it.name == "备份课表" }
        assertEquals(default.id, newTable.timeSlotSchemeId)
        assertEquals(1, courseDao.getByTableOnce(newTable.id).size)
    }

    @Test
    fun `合并导入_不同作息方案则新建`() = runBlocking {
        initializer().initialize()

        repository.importMerge(backupData(schemeName = "全新作息"))

        assertEquals(2, schemeDao.count())
        val newTable = courseTableDao.getAllOnce().first { it.name == "备份课表" }
        assertEquals("全新作息", schemeDao.getByIdOnce(newTable.timeSlotSchemeId)!!.name)
    }

    @Test
    fun `错误文件_解析校验失败且不触碰已有数据`() = runBlocking {
        initializer().initialize()
        val scheme = schemeDao.observeAll().first().single()
        val tableId = insertTable("唯一课表", scheme.id)

        // 构造引用不一致(课程指向不存在的课表)的损坏备份
        val root = JSONObject(BackupJson.toJson(backupData()))
        root.getJSONArray("courses").getJSONObject(0).put("courseTableId", 999)
        val badText = root.toString()

        try {
            BackupJson.fromJson(badText)
            fail("损坏的备份应当校验失败")
        } catch (_: BackupFormatException) {
            // 预期:解析层拦截,不进入仓库写库
        }

        val tables = courseTableDao.getAllOnce()
        assertEquals(1, tables.size)
        assertEquals(tableId, tables.single().id)
        assertEquals(0, courseDao.getAllOnce().size)
    }
}