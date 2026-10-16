package dev.chungjungsoo.gptmobile.smoke.vault

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.smoke.SmokeActivityTest
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@SmokeFeature("vault")
class VaultSyncSmokeTest : SmokeActivityTest("vault") {
    @Test
    fun vaultSync_opensFromSettingsAndCloses() {
        val settingsRow = assumeFeature("obsidian_sync_settings_title")
        val syncNow = assumeFeature("obsidian_sync_now")
        val heading = assumeFeature("obsidian_github_heading")
        launchSeededHome()
        waitForText(targetContext.getString(R.string.new_chat), timeoutMs = 45_000)
        clickContentDescription(targetContext.getString(R.string.settings))
        waitForText(targetContext.getString(R.string.appearance))
        val rowLabel = settingsRow.substringAfterLast(" ").ifBlank { "vault" }
        clickTextContaining(rowLabel, timeoutMs = 15_000)
        waitForText(heading)
        waitForText(syncNow)
        clickContentDescription(targetContext.getString(R.string.go_back))
        waitForText(targetContext.getString(R.string.appearance))
    }
}
