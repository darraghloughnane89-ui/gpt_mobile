package dev.chungjungsoo.gptmobile.presentation.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.fadeSpring
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.playfulSpring
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.snapSpring

internal fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = playfulSpring()

internal fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snapSpring()

internal fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = fadeSpring()
