package dev.chungjungsoo.gptmobile.smoke.phonecontrol

import android.app.Activity
import android.app.Instrumentation
import android.provider.Settings
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chungjungsoo.gptmobile.smoke.FeatureSmokeHarness
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import org.hamcrest.Matchers.allOf
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phone-control settings gates: setup pages, allowlist, action log, vault, and routine approval.
 * Skips until [dev.chungjungsoo.gptmobile.presentation.ui.phonecontrol.PhoneControlViewModel] is in the APK.
 */
@RunWith(AndroidJUnit4::class)
@SmokeFeature("phone-control")
class PhoneControlSmokeTest :
    FeatureSmokeHarness(
        markerClass = "dev.chungjungsoo.gptmobile.presentation.ui.phonecontrol.PhoneControlViewModel",
        feature = "phone-control"
    ) {
    @Test
    fun setupButtonsOpenTheSystemPagesAndTheGatesStayOnScreen() {
        Intents.init()
        try {
            Intents.intending(hasAction(Settings.ACTION_ACCESSIBILITY_SETTINGS)).respondWith(canceled())
            Intents.intending(hasAction(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)).respondWith(canceled())
            Intents.intending(hasAction(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)).respondWith(canceled())
            launchMain()
            openSettings()
            clickText(featureText("phone_control_title"))
            waitForText(featureText("phone_control_stop"))
            if (hasText(featureText("phone_control_enable_accessibility"))) {
                clickText(featureText("phone_control_enable_accessibility"))
                Intents.intended(hasAction(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                clickText(featureText("phone_control_enable_notifications"))
                Intents.intended(hasAction(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                clickText(featureText("phone_control_enable_autofill"))
                Intents.intended(
                    allOf(
                        hasAction(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE),
                        hasData("package:${context.packageName}")
                    )
                )
            }
            clickText(featureText("phone_control_allowlist"))
            waitForText(featureText("phone_control_allowlist"))
            goBack()
            clickText(featureText("phone_control_action_log"))
            waitForText(featureText("phone_control_log_empty"))
            goBack()
            waitForText(featureText("phone_control_vault_empty"))
            waitForText(featureText("phone_control_secret"))
            clickText(featureText("phone_control_approve_routine"))
            clickText(featureText("phone_control_save_password_manager"))
            waitForText(featureText("phone_control_vault_heading"))
        } finally {
            Intents.release()
        }
    }

    private fun canceled() = Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
}
