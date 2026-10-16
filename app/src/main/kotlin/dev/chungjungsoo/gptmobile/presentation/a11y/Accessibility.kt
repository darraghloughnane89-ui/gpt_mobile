package dev.chungjungsoo.gptmobile.presentation.a11y

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

internal const val MIN_TOUCH_TARGET_DP = 48
internal const val MAX_SUPPORTED_FONT_SCALE = 2f
private const val TEXT_CONTRAST_AA = 4.5
private const val UI_CONTRAST_AA = 3.0

internal enum class TextRole {
    BODY,
    CHROME
}

/** Body copy wraps at large font scales. Single-line ellipsis is reserved for app-bar chrome. */
internal fun allowsSingleLineEllipsis(role: TextRole): Boolean = role == TextRole.CHROME

fun Modifier.minimumTouchTarget(): Modifier = this.sizeIn(
    minWidth = MIN_TOUCH_TARGET_DP.dp,
    minHeight = MIN_TOUCH_TARGET_DP.dp
)

/** Animator duration scale of 0 is the system "remove animations" setting. */
internal fun isReducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

internal fun <T> motionSpec(
    reducedMotion: Boolean,
    full: FiniteAnimationSpec<T>
): FiniteAnimationSpec<T> = if (reducedMotion) snap() else full

@Composable
fun prefersReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember {
        mutableStateOf(isReducedMotion(readAnimatorDurationScale(context.contentResolver)))
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = isReducedMotion(readAnimatorDurationScale(context.contentResolver))
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}

internal fun readAnimatorDurationScale(resolver: ContentResolver): Float = try {
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
} catch (_: SecurityException) {
    1f
}

internal fun composite(foreground: Color, background: Color): Color {
    val alpha = foreground.alpha
    if (alpha >= 1f) return Color(foreground.red, foreground.green, foreground.blue, 1f)
    return Color(
        red = foreground.red * alpha + background.red * (1f - alpha),
        green = foreground.green * alpha + background.green * (1f - alpha),
        blue = foreground.blue * alpha + background.blue * (1f - alpha),
        alpha = 1f
    )
}

internal fun contrastRatio(foreground: Color, background: Color): Double {
    val blended = composite(foreground, background)
    val lighter = max(blended.luminance(), background.luminance())
    val darker = min(blended.luminance(), background.luminance())
    return (lighter + 0.05) / (darker + 0.05)
}

internal fun meetsTextContrast(foreground: Color, background: Color): Boolean =
    contrastRatio(foreground, background) >= TEXT_CONTRAST_AA

internal fun meetsUiContrast(foreground: Color, background: Color): Boolean =
    contrastRatio(foreground, background) >= UI_CONTRAST_AA
