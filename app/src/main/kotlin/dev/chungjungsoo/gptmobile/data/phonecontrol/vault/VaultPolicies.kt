/*
 * Biometric-before-fill gate.
 * The handoff shape follows Bitwarden Android (GPL-3.0): the model receives
 * "fill requested" and never the password. Package signature and web domain
 * bind which vault entry may be offered.
 */
package dev.chungjungsoo.gptmobile.data.phonecontrol.vault

enum class AutofillTrigger {
    FIELD_FOCUS,
    AUTOFILL_UI_TAP,
    MODEL_REQUEST
}

object AutofillIntentPolicy {
    fun allow(trigger: AutofillTrigger, biometricConfirmed: Boolean): Boolean {
        val explicitIntent = trigger == AutofillTrigger.FIELD_FOCUS || trigger == AutofillTrigger.AUTOFILL_UI_TAP
        return explicitIntent && biometricConfirmed
    }

    fun modelSignal(): String = "fill requested"
}

data class CredentialBinding(
    val packageName: String,
    val certSha256: String,
    val webDomain: String? = null
) {
    fun matches(packageName: String, certSha256: String, webDomain: String?): Boolean {
        if (!this.packageName.equals(packageName, ignoreCase = true)) return false
        if (!this.certSha256.equals(certSha256, ignoreCase = true)) return false
        val requiredDomain = this.webDomain?.trim()?.lowercase().orEmpty()
        val actual = webDomain?.trim()?.lowercase().orEmpty()
        if (requiredDomain.isEmpty()) return actual.isEmpty()
        return actual == requiredDomain || actual.endsWith(".$requiredDomain")
    }
}

data class VaultEntryView(
    val id: String,
    val title: String,
    val domain: String?
)

object VaultCapability {
    fun forModel(entries: List<VaultEntryView>): String {
        if (entries.isEmpty()) return "No Robin vault entries."
        return entries.joinToString("\n") { entry ->
            "id=${entry.id} title=${entry.title} domain=${entry.domain.orEmpty()} secret=hidden"
        }
    }

    fun secretForModel(@Suppress("UNUSED_PARAMETER") secret: ByteArray?): String =
        throw IllegalStateException("The model cannot read vault secrets.")
}

object VaultSyncPolicy {
    private val blocked = setOf("github", "obsidian", "drive", "google-drive")

    fun maySyncTo(target: String): Boolean = target.lowercase() !in blocked

    fun plaintextExportAllowed(): Boolean = false
}

data class CapabilityToken(
    val id: String,
    val action: String
)

object CapabilityTokens {
    fun fillToken(id: String): CapabilityToken = CapabilityToken(id, "fill requested")

    fun reveal(token: CapabilityToken): String = token.action
}
