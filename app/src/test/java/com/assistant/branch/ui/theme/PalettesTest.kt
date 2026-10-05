package com.assistant.branch.ui.theme

import com.assistant.branch.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PalettesTest {

    @Test
    fun `every palette has distinct seed color`() {
        val seeds = AccentPalette.entries.map { it.seed }.toSet()
        assertEquals(AccentPalette.entries.size, seeds.size)
    }

    @Test
    fun `every palette produces distinct primary in dark scheme`() {
        val primaries = AccentPalette.entries.map { palette ->
            accentColorsFor(palette).dark.primary
        }
        // Все primary разные
        assertEquals(AccentPalette.entries.size, primaries.toSet().size)
    }

    @Test
    fun `every palette produces distinct primary in light scheme`() {
        val primaries = AccentPalette.entries.map { palette ->
            accentColorsFor(palette).light.primary
        }
        assertEquals(AccentPalette.entries.size, primaries.toSet().size)
    }

    @Test
    fun `light and dark versions of same palette have different background`() {
        AccentPalette.entries.forEach { palette ->
            val colors = accentColorsFor(palette)
            assertNotEquals(
                "Palette ${palette.name}: light and dark bg must differ",
                colors.light.background,
                colors.dark.background,
            )
        }
    }

    @Test
    fun `each palette has a non-blank display name and emoji`() {
        AccentPalette.entries.forEach { palette ->
            assertTrue(
                "Palette ${palette.name}: displayName must not be blank",
                palette.displayName.isNotBlank()
            )
            assertTrue(
                "Palette ${palette.name}: emoji must not be blank",
                palette.emoji.isNotBlank()
            )
        }
    }

    @Test
    fun `light scheme has light background and dark has dark`() {
        AccentPalette.entries.forEach { palette ->
            val colors = accentColorsFor(palette)
            // Light bg по яркости выше (каждая компонента RGB > 0.8 примерно)
            val lightLuminance = luminanceOf(colors.light.background)
            val darkLuminance = luminanceOf(colors.dark.background)
            assertTrue(
                "Palette ${palette.name}: light bg should be brighter than dark",
                lightLuminance > darkLuminance,
            )
        }
    }

    private fun luminanceOf(color: androidx.compose.ui.graphics.Color): Float {
        return (color.red + color.green + color.blue) / 3f
    }
}

class ThemeModeTest {

    @Test
    fun `fromString parses valid values`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromString("system"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromString(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromString("garbage"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromString("dark"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromString("Dark"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromString("light"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromString("LIGHT"))
    }

    @Test
    fun `toString returns lowercase name`() {
        assertEquals("system", ThemeMode.SYSTEM.toString())
        assertEquals("dark", ThemeMode.DARK.toString())
        assertEquals("light", ThemeMode.LIGHT.toString())
    }

    @Test
    fun `themeMode roundtrips through string`() {
        ThemeMode.entries.forEach { mode ->
            val s = mode.toString()
            val parsed = ThemeMode.fromString(s)
            assertEquals(mode, parsed)
        }
    }
}
