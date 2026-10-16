package dev.chungjungsoo.gptmobile.data.phonecontrol

import android.text.InputType
import android.view.View
import dev.chungjungsoo.gptmobile.data.phonecontrol.autofill.AutofillFieldParser
import dev.chungjungsoo.gptmobile.data.phonecontrol.autofill.AutofillNode
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.Redaction
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.ScreenshotPolicy
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.ScreenshotRedactor
import dev.chungjungsoo.gptmobile.data.phonecontrol.vault.AutofillIntentPolicy
import dev.chungjungsoo.gptmobile.data.phonecontrol.vault.AutofillTrigger
import dev.chungjungsoo.gptmobile.data.phonecontrol.vault.CapabilityTokens
import dev.chungjungsoo.gptmobile.data.phonecontrol.vault.CredentialBinding
import dev.chungjungsoo.gptmobile.data.phonecontrol.vault.VaultCapability
import dev.chungjungsoo.gptmobile.data.phonecontrol.vault.VaultEntryView
import dev.chungjungsoo.gptmobile.data.phonecontrol.vault.VaultSyncPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillAndVaultTest {
    @Test
    fun autofillParser_passwordAndUsernameHints_keepIdsOnly() {
        val parsed = AutofillFieldParser().parse(
            listOf(
                AutofillNode(id = "user", hints = listOf(View.AUTOFILL_HINT_USERNAME)),
                AutofillNode(id = "pass", hints = listOf(View.AUTOFILL_HINT_PASSWORD), webDomain = "example.com")
            ),
            "com.example.login"
        )
        assertEquals("user", parsed.usernameId)
        assertEquals("pass", parsed.passwordId)
        assertEquals("example.com", parsed.webDomain)
        assertTrue(parsed.isValid())
        assertFalse(parsed.toString().contains("secret-value"))
    }

    @Test
    fun autofillParser_passwordInputType_isDetected() {
        val parsed = AutofillFieldParser().parse(
            listOf(
                AutofillNode(id = "pass", inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            ),
            "com.example.login"
        )
        assertEquals("pass", parsed.passwordId)
        assertNull(parsed.usernameId)
    }

    @Test
    fun autofillIntent_modelRequest_isDenied() {
        assertFalse(AutofillIntentPolicy.allow(AutofillTrigger.MODEL_REQUEST, biometricConfirmed = true))
    }

    @Test
    fun autofillIntent_fieldFocusWithoutBiometric_isDenied() {
        assertFalse(AutofillIntentPolicy.allow(AutofillTrigger.FIELD_FOCUS, biometricConfirmed = false))
    }

    @Test
    fun autofillIntent_autofillTapWithBiometric_isAllowed() {
        assertTrue(AutofillIntentPolicy.allow(AutofillTrigger.AUTOFILL_UI_TAP, biometricConfirmed = true))
        assertEquals("fill requested", AutofillIntentPolicy.modelSignal())
    }

    @Test
    fun credentialBinding_wrongSignatureOrDomain_rejects() {
        val binding = CredentialBinding("com.example.login", "aa", "example.com")
        assertTrue(binding.matches("com.example.login", "aa", "login.example.com"))
        assertFalse(binding.matches("com.example.login", "bb", "example.com"))
        assertFalse(binding.matches("com.other", "aa", "example.com"))
        assertFalse(binding.matches("com.example.login", "aa", "evil.test"))
    }

    @Test
    fun vaultCapability_modelView_hidesSecret() {
        val view = VaultCapability.forModel(listOf(VaultEntryView("1", "Work", "example.com")))
        assertTrue(view.contains("secret=hidden"))
        assertFalse(view.contains("hunter2"))
        try {
            VaultCapability.secretForModel("hunter2".encodeToByteArray())
            throw AssertionError("secret should not be readable")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("cannot read"))
        }
    }

    @Test
    fun vaultSync_githubObsidianAndDrive_areBlocked() {
        assertFalse(VaultSyncPolicy.maySyncTo("github"))
        assertFalse(VaultSyncPolicy.maySyncTo("obsidian"))
        assertFalse(VaultSyncPolicy.maySyncTo("drive"))
        assertFalse(VaultSyncPolicy.plaintextExportAllowed())
    }

    @Test
    fun capabilityToken_revealsOnlyTheFillSignal() {
        assertEquals("fill requested", CapabilityTokens.reveal(CapabilityTokens.fillToken("abc")))
    }

    @Test
    fun redaction_passwordNodeLogAndScreenshot_areScrubbed() {
        val node = UiNode(text = "hunter2", isPassword = true, bounds = UiBounds(1, 2, 3, 4))
        val redacted = Redaction.redactNode(node)
        assertEquals(Redaction.REDACTED, redacted.text)
        assertFalse(Redaction.treeContainsSecret(redacted.text, "hunter2"))
        assertEquals("password=[redacted]", Redaction.redactLog("password=hunter2"))
        assertFalse(ScreenshotPolicy.allowCapture(secureWindow = true, financeApp = false, passwordFieldVisible = false))
        assertFalse(ScreenshotPolicy.allowCapture(secureWindow = false, financeApp = true, passwordFieldVisible = false))
        assertFalse(ScreenshotPolicy.allowCapture(secureWindow = false, financeApp = false, passwordFieldVisible = true))
        assertTrue(ScreenshotPolicy.preferTree(treeNonEmpty = true))
        assertEquals(1, ScreenshotRedactor.passwordRects(node).single().left)
    }
}
