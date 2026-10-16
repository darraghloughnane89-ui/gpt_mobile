package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

fun <T> spatialSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.78f, stiffness = 420f)

fun <T> playfulSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.62f, stiffness = 520f)

fun <T> snapSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.86f, stiffness = 900f)

fun <T> fadeSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 2800f)
