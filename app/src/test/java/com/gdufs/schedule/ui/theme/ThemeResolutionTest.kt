package com.gdufs.schedule.ui.theme

import com.gdufs.schedule.data.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [resolveDarkTheme] 的主题模式解析测试:
 * 跟随系统采用系统亮暗,浅色/深色强制对应模式。
 */
class ThemeResolutionTest {

    @Test
    fun `跟随系统_系统深色时用深色`() {
        assertEquals(true, resolveDarkTheme(ThemeMode.SYSTEM, systemDark = true))
    }

    @Test
    fun `跟随系统_系统浅色时用浅色`() {
        assertEquals(false, resolveDarkTheme(ThemeMode.SYSTEM, systemDark = false))
    }

    @Test
    fun `浅色模式_系统深色仍用浅色`() {
        assertEquals(false, resolveDarkTheme(ThemeMode.LIGHT, systemDark = true))
    }

    @Test
    fun `深色模式_系统浅色仍用深色`() {
        assertEquals(true, resolveDarkTheme(ThemeMode.DARK, systemDark = false))
    }
}