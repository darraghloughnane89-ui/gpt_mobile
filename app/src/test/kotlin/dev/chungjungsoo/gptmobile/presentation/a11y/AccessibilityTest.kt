package dev.chungjungsoo.gptmobile.presentation.a11y

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import dev.chungjungsoo.gptmobile.data.bot.BotAvatarPalette
import dev.chungjungsoo.gptmobile.presentation.theme.backgroundDark
import dev.chungjungsoo.gptmobile.presentation.theme.backgroundLight
import dev.chungjungsoo.gptmobile.presentation.theme.errorDark
import dev.chungjungsoo.gptmobile.presentation.theme.errorLight
import dev.chungjungsoo.gptmobile.presentation.theme.onBackgroundDark
import dev.chungjungsoo.gptmobile.presentation.theme.onBackgroundLight
import dev.chungjungsoo.gptmobile.presentation.theme.onErrorDark
import dev.chungjungsoo.gptmobile.presentation.theme.onErrorLight
import dev.chungjungsoo.gptmobile.presentation.theme.onPrimaryDark
import dev.chungjungsoo.gptmobile.presentation.theme.onPrimaryLight
import dev.chungjungsoo.gptmobile.presentation.theme.onSurfaceDark
import dev.chungjungsoo.gptmobile.presentation.theme.onSurfaceLight
import dev.chungjungsoo.gptmobile.presentation.theme.onSurfaceVariantDark
import dev.chungjungsoo.gptmobile.presentation.theme.onSurfaceVariantLight
import dev.chungjungsoo.gptmobile.presentation.theme.outlineDark
import dev.chungjungsoo.gptmobile.presentation.theme.outlineLight
import dev.chungjungsoo.gptmobile.presentation.theme.primaryDark
import dev.chungjungsoo.gptmobile.presentation.theme.primaryLight
import dev.chungjungsoo.gptmobile.presentation.theme.surfaceDark
import dev.chungjungsoo.gptmobile.presentation.theme.surfaceLight
import dev.chungjungsoo.gptmobile.presentation.theme.surfaceVariantLight
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityTest {
    @Test
    fun themeTextPairsMeetAaInLightAndDark() {
        val light = listOf(
            onBackgroundLight to backgroundLight,
            onSurfaceLight to surfaceLight,
            onSurfaceVariantLight to surfaceLight,
            onPrimaryLight to primaryLight,
            onErrorLight to errorLight
        )
        val dark = listOf(
            onBackgroundDark to backgroundDark,
            onSurfaceDark to surfaceDark,
            onSurfaceVariantDark to surfaceDark,
            onPrimaryDark to primaryDark,
            onErrorDark to errorDark
        )
        (light + dark).forEach { (foreground, background) ->
            assertTrue(meetsTextContrast(foreground, background))
        }
    }

    @Test
    fun outlinesMeetUiContrast() {
        assertTrue(meetsUiContrast(outlineLight, backgroundLight))
        assertTrue(meetsUiContrast(outlineDark, backgroundDark))
    }

    @Test
    fun chatBadgeUsesPrimaryInsteadOfTheLowContrastBrandGreen() {
        val retiredGreen = Color(0xFF00A67D)
        val badgePlate = Color(0xFFF4FBF7)
        assertFalse(meetsTextContrast(retiredGreen, backgroundLight))
        assertTrue(meetsTextContrast(primaryLight, badgePlate))
    }

    @Test
    fun fadedThinkingTextFailsBodyContrast() {
        assertFalse(meetsTextContrast(onSurfaceVariantLight.copy(alpha = 0.7f), surfaceVariantLight))
        assertTrue(meetsTextContrast(onSurfaceVariantLight, surfaceVariantLight))
    }

    @Test
    fun botAvatarColorsKeepWhiteTextReadable() {
        BotAvatarPalette.colors.forEach { hex ->
            assertTrue(meetsTextContrast(Color.White, colorFromHex(hex)))
        }
    }

    @Test
    fun reducedMotionIsOnlyTheSystemOffScale() {
        assertTrue(isReducedMotion(0f))
        assertFalse(isReducedMotion(0.5f))
        assertFalse(isReducedMotion(1f))
        assertTrue(motionSpec(reducedMotion = true, full = tween<Float>()) is SnapSpec)
        assertTrue(motionSpec(reducedMotion = false, full = tween<Float>()) is TweenSpec)
    }

    @Test
    fun bodyCopyWrapsAndTouchTargetsStayAtLeast48Dp() {
        assertFalse(allowsSingleLineEllipsis(TextRole.BODY))
        assertTrue(allowsSingleLineEllipsis(TextRole.CHROME))
        assertTrue(MIN_TOUCH_TARGET_DP >= 48)
        assertTrue(MAX_SUPPORTED_FONT_SCALE >= 2f)
    }

    private fun colorFromHex(hex: String): Color {
        val value = hex.removePrefix("#").toLong(16)
        return Color(
            red = ((value shr 16) and 0xFF) / 255f,
            green = ((value shr 8) and 0xFF) / 255f,
            blue = (value and 0xFF) / 255f
        )
    }
}
