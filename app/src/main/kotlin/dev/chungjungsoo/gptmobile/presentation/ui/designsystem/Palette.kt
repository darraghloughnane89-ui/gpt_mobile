package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

internal val Ember = Color(0xFFFF5C3A)
internal val EmberDeep = Color(0xFFB42318)
internal val Amber = Color(0xFFFFB15A)
internal val Ink = Color(0xFF000000)
internal val Paper = Color(0xFFFFF8F4)
internal val WarmWhite = Color(0xFFF6EFEA)
internal val Clay = Color(0xFFE7C4B0)

val AmoledDarkScheme = darkColorScheme(
    primary = Ember,
    onPrimary = Color(0xFF2B0903),
    primaryContainer = Color(0xFF7A2614),
    onPrimaryContainer = Color(0xFFFFD9D0),
    secondary = Amber,
    onSecondary = Color(0xFF2A1700),
    secondaryContainer = Color(0xFF5A3A12),
    onSecondaryContainer = Color(0xFFFFE4C4),
    tertiary = Clay,
    onTertiary = Color(0xFF2C1810),
    tertiaryContainer = Color(0xFF4A3328),
    onTertiaryContainer = Color(0xFFFFE6D6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Ink,
    onBackground = WarmWhite,
    surface = Ink,
    onSurface = WarmWhite,
    surfaceVariant = Color(0xFF2A2420),
    onSurfaceVariant = Color(0xFFD4C4BA),
    outline = Color(0xFF9C8B82),
    outlineVariant = Color(0xFF4A3F39),
    scrim = Ink,
    inverseSurface = WarmWhite,
    inverseOnSurface = Color(0xFF1C1614),
    inversePrimary = EmberDeep,
    surfaceDim = Ink,
    surfaceBright = Color(0xFF3A332E),
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Color(0xFF0E0C0B),
    surfaceContainer = Color(0xFF161311),
    surfaceContainerHigh = Color(0xFF1E1A17),
    surfaceContainerHighest = Color(0xFF27221E)
)

val PaperLightScheme = lightColorScheme(
    primary = EmberDeep,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = Color(0xFF3F0500),
    secondary = Color(0xFF8A4F00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDDB8),
    onSecondaryContainer = Color(0xFF2C1600),
    tertiary = Color(0xFF6B4A3A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3DDD2),
    onTertiaryContainer = Color(0xFF28170F),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Paper,
    onBackground = Color(0xFF1C1210),
    surface = Paper,
    onSurface = Color(0xFF1C1210),
    surfaceVariant = Color(0xFFF3E4DC),
    onSurfaceVariant = Color(0xFF56443D),
    outline = Color(0xFF8A7168),
    outlineVariant = Color(0xFFDDC3B8),
    scrim = Ink,
    inverseSurface = Color(0xFF322824),
    inverseOnSurface = Color(0xFFFFEDE7),
    inversePrimary = Ember,
    surfaceDim = Color(0xFFE8D9D2),
    surfaceBright = Paper,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EA),
    surfaceContainer = Color(0xFFFBE8DF),
    surfaceContainerHigh = Color(0xFFF5E0D4),
    surfaceContainerHighest = Color(0xFFEFD6C8)
)

fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = maxOf(foreground.relativeLuminance(), background.relativeLuminance())
    val darker = minOf(foreground.relativeLuminance(), background.relativeLuminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

internal fun Color.relativeLuminance(): Float {
    fun channel(value: Float): Float = if (value <= 0.04045f) {
        value / 12.92f
    } else {
        ((value + 0.055f) / 1.055f).pow(2.4f)
    }
    val red = channel(red)
    val green = channel(green)
    val blue = channel(blue)
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
