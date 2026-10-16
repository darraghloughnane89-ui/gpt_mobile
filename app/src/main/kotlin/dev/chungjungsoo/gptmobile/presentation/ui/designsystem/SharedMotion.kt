package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedChatContainer(chatId: Int): Modifier {
    if (chatId <= 0) return this
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalAnimatedVisibilityScope.current ?: return this
    return with(shared) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key = "chat-container-$chatId"),
            animatedVisibilityScope = animated,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.78f, stiffness = 380f) },
            enter = fadeIn(fadeSpring()),
            exit = fadeOut(fadeSpring()),
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
        )
    }
}
