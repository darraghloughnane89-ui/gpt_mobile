package dev.chungjungsoo.gptmobile.presentation.phone.widget

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.material3.ColorProviders

/**
 * Day and night dynamic colours are both stored on the widget, so the launcher
 * can follow the system theme without waiting for another update.
 */
@Composable
internal fun HomeWidgetTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = runCatching {
        ColorProviders(
            dynamicLightColorScheme(context),
            dynamicDarkColorScheme(context)
        )
    }.getOrElse {
        ColorProviders(lightColorScheme(), darkColorScheme())
    }
    GlanceTheme(colors = colors, content = content)
}
