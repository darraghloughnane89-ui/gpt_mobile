package dev.chungjungsoo.gptmobile.smoke.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.smoke.FeatureSmokeHarness
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First-run checklist: skip at any step, MiMo suggestion, voice does not open the mic, empty nest.
 * Skips until [dev.chungjungsoo.gptmobile.presentation.ui.onboarding.OnboardingViewModel] is in the APK.
 */
@RunWith(AndroidJUnit4::class)
@SmokeFeature("onboarding")
class OnboardingSmokeTest :
    FeatureSmokeHarness(
        markerClass = "dev.chungjungsoo.gptmobile.presentation.ui.onboarding.OnboardingViewModel",
        feature = "onboarding"
    ) {
    override fun prepareAccounts() {
        clearPlatforms()
        File(context.filesDir, "datastore/onboarding.preferences_pb").delete()
    }

    @Test
    fun skippableSetupStaysFinishedAndShowsNestEmptyStates() {
        launchMain()
        waitForText(featureText("onboarding_skip_setup"))
        clickText(featureText("onboarding_more"))
        waitForText(featureText("onboarding_voice"), substring = true)
        clickText(context.getString(R.string.get_started))
        waitForText(featureText("onboarding_provider_title"))
        waitForText(featureText("onboarding_use_mimo"))
        waitForText("xiaomimimo", substring = true)
        clickText(featureText("onboarding_skip"))
        waitForText(featureText("onboarding_import_title"))
        clickText(featureText("onboarding_skip"))
        waitForText(featureText("onboarding_voice_note"))
        clickText(context.getString(R.string.next))
        waitForText(featureText("onboarding_open_bots"))
        clickText(context.getString(R.string.done))
        waitForText(featureText("empty_chats_title"))

        relaunchMain()
        waitForHome()
        assertFalse(hasText(featureText("onboarding_skip_setup")))

        openSettings()
        clickText(featureText("library_skills"))
        waitForText(featureText("shell_skills_title"))
        goBack()
        clickText(featureText("library_memory"))
        waitForText(featureText("shell_memory_title"))
    }
}
