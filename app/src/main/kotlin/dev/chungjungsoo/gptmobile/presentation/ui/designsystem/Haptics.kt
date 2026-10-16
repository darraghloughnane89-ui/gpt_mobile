package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

enum class HapticEvent {
    Tick,
    Confirm,
    Reject
}

@Composable
fun rememberHaptic(): (HapticEvent) -> Unit {
    val view = LocalView.current
    return remember(view) { { event -> view.performBrandHaptic(event) } }
}

internal fun View.performBrandHaptic(event: HapticEvent) {
    val constant = when (event) {
        HapticEvent.Tick -> HapticFeedbackConstants.CLOCK_TICK
        HapticEvent.Confirm -> HapticFeedbackConstants.CONFIRM
        HapticEvent.Reject -> HapticFeedbackConstants.REJECT
    }
    performHapticFeedback(constant)
}
