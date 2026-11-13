package com.onthecrow.wallper.settings.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {

    @Test
    fun `the system mode follows the system's theme`() {
        assertTrue(ThemeMode.SYSTEM.isDark(isSystemInDarkTheme = true))
        assertFalse(ThemeMode.SYSTEM.isDark(isSystemInDarkTheme = false))
    }

    @Test
    fun `light and dark ignore the system's theme`() {
        assertFalse(ThemeMode.LIGHT.isDark(isSystemInDarkTheme = true))
        assertFalse(ThemeMode.LIGHT.isDark(isSystemInDarkTheme = false))
        assertTrue(ThemeMode.DARK.isDark(isSystemInDarkTheme = true))
        assertTrue(ThemeMode.DARK.isDark(isSystemInDarkTheme = false))
    }
}
