package dev.chungjungsoo.gptmobile.data.phonecontrol.vault

import dev.chungjungsoo.gptmobile.data.database.dao.PhoneControlDao
import dev.chungjungsoo.gptmobile.data.database.entity.RobinVaultMeta
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RobinVaultStore @Inject constructor(
    private val vault: RobinHardwareVault,
    private val dao: PhoneControlDao
) {
    suspend fun listForModel(): String = VaultCapability.forModel(
        dao.vaultMeta().map { VaultEntryView(it.id, it.title, it.webDomain) }
    )

    suspend fun save(meta: RobinVaultMeta, secret: ByteArray) {
        if (VaultSyncPolicy.plaintextExportAllowed()) {
            error("Plaintext vault export is disabled.")
        }
        vault.put(meta.secretRef, secret)
        secret.fill(0)
        dao.upsertVaultMeta(meta.copy(username = meta.username))
    }

    suspend fun readForAutofill(
        id: String,
        packageName: String,
        certSha256: String,
        webDomain: String?,
        biometricConfirmed: Boolean
    ): ByteArray? {
        if (!AutofillIntentPolicy.allow(AutofillTrigger.AUTOFILL_UI_TAP, biometricConfirmed)) return null
        val meta = dao.vaultMeta().firstOrNull { it.id == id } ?: return null
        if (meta.packageName.isNullOrBlank() || meta.certSha256.isNullOrBlank()) return null
        val binding = CredentialBinding(meta.packageName, meta.certSha256, meta.webDomain)
        if (!binding.matches(packageName, certSha256, webDomain)) return null
        return vault.read(meta.secretRef)
    }

    suspend fun readFirstMatch(packageName: String, certSha256: String, webDomain: String?): ByteArray? {
        val meta = dao.vaultMeta().firstOrNull { entry ->
            val entryPackage = entry.packageName
            val entryCert = entry.certSha256
            !entryPackage.isNullOrBlank() &&
                !entryCert.isNullOrBlank() &&
                CredentialBinding(entryPackage, entryCert, entry.webDomain).matches(packageName, certSha256, webDomain)
        } ?: return null
        return readForAutofill(meta.id, packageName, certSha256, webDomain, biometricConfirmed = true)
    }
}
