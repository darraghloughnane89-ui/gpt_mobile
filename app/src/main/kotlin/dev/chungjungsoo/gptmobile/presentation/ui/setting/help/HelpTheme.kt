@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package dev.chungjungsoo.gptmobile.presentation.ui.setting.help

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import dev.chungjungsoo.gptmobile.presentation.common.LocalThemeMode

private val Ember = Color(0xFFFF5C3A)
private val EmberDeep = Color(0xFFB42318)
private val Amber = Color(0xFFFFB15A)
private val Ink = Color(0xFF000000)
private val Paper = Color(0xFFFFF8F4)
private val WarmWhite = Color(0xFFF6EFEA)
private val Clay = Color(0xFFE7C4B0)

private val HelpDarkScheme = darkColorScheme(
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

private val HelpLightScheme = lightColorScheme(
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

object HelpSpace {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

private val HelpShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

private fun outfit(weight: Int) = Font(
    resId = R.font.outfit_variable,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

private fun mono(weight: Int) = Font(
    resId = R.font.jetbrains_mono_variable,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

private val OutfitFamily = FontFamily(
    outfit(400),
    outfit(500),
    outfit(600)
)

private val MonoFamily = FontFamily(
    mono(400),
    mono(500)
)

private val HelpTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    labelLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)

internal val HelpCodeStyle = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.em
)

@Composable
fun HelpTheme(content: @Composable () -> Unit) {
    val dark = when (LocalThemeMode.current) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) HelpDarkScheme else HelpLightScheme,
        shapes = HelpShapes,
        typography = HelpTypography,
        content = content
    )
}
