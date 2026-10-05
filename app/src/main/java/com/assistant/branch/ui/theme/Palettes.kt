package com.assistant.branch.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Набор цветовых палитр приложения.
 *
 * Каждая палитра определяет пару ColorScheme (light + dark) с собственным accent.
 * Существующие компоненты читают accent через [androidx.compose.material3.MaterialTheme.colorScheme.primary],
 * поэтому достаточно подменить схему — всё перекрасится автоматически.
 */
enum class AccentPalette(
    val displayName: String,
    val emoji: String,
    val seedArgb: Int,
) {
    VIOLET("Фиолет", "💜", 0xFF7C5CFF.toInt()),
    MINT("Мята", "🌿", 0xFF34D399.toInt()),
    OCEAN("Океан", "🌊", 0xFF38BDF8.toInt()),
    SUNSET("Закат", "🌅", 0xFFFB923C.toInt()),
    ROSE("Роза", "🌹", 0xFFFB7185.toInt());

    val seed: Color get() = Color(seedArgb)
}

data class PaletteColors(
    val light: ColorScheme,
    val dark: ColorScheme,
)

/**
 * Строит пары ColorScheme для каждой палитры.
 *
 * Background/surface/text берутся из базовой тёмной/светлой темы,
 * accent-цвета — из палитры. Остальные цвета (errors, secondary)
 * общие для всех палитр.
 */
fun accentColorsFor(palette: AccentPalette): PaletteColors {
    val primary = Color(palette.seedArgb)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = primary.copy(alpha = 0.85f)
    val onPrimaryContainer = Color(0xFF1A1230)

    val lightSurface = Color(0xFFFAFAFC)
    val lightSurfaceVariant = Color(0xFFEFF1F6)
    val onLightSurface = Color(0xFF14161E)
    val onLightSurfaceVariant = Color(0xFF54596A)
    val lightBg = Color(0xFFF6F6F9)
    val lightOutline = Color(0xFFD8DAE3)

    val light = lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = primary.copy(alpha = 0.7f),
        onSecondary = onPrimary,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onPrimaryContainer,
        background = lightBg,
        onBackground = onLightSurface,
        surface = lightSurface,
        onSurface = onLightSurface,
        surfaceVariant = lightSurfaceVariant,
        onSurfaceVariant = onLightSurfaceVariant,
        outline = lightOutline,
        outlineVariant = lightOutline.copy(alpha = 0.6f),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
    )

    val dark = darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = primary.copy(alpha = 0.7f),
        onSecondary = onPrimary,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onPrimaryContainer,
        background = BgDeep,
        onBackground = TextHi,
        surface = BgSurface,
        onSurface = TextHi,
        surfaceVariant = BgElevated,
        onSurfaceVariant = TextLo,
        outline = BranchLine,
        outlineVariant = BranchLine.copy(alpha = 0.5f),
        error = ErrorRed,
        onError = Color(0xFFFFFFFF),
    )

    return PaletteColors(light = light, dark = dark)
}
