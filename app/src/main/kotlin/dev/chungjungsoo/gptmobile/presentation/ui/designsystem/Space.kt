package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

object Elevation {
    val none = 0.dp
    val hairline = 1.dp
    val raised = 3.dp
    val overlay = 8.dp
    val sheet = 12.dp
}

val BrandShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

fun userBubbleShape() = RoundedCornerShape(
    topStart = 22.dp,
    topEnd = 22.dp,
    bottomEnd = 8.dp,
    bottomStart = 22.dp
)

fun assistantBubbleShape() = RoundedCornerShape(22.dp)
