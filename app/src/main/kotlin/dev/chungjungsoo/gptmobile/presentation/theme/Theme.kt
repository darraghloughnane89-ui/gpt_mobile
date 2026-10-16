package dev.chungjungsoo.gptmobile.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.AmoledDarkScheme
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.BrandShapes
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.PaperLightScheme

@Composable
fun GPTMobileTheme(
    dynamicTheme: DynamicTheme = DynamicTheme.OFF,
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDynamicColor = dynamicTheme == DynamicTheme.ON
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = when {
        useDynamicColor -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDarkTheme -> AmoledDarkScheme
        else -> PaperLightScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = BrandShapes,
        content = content
    )
}
