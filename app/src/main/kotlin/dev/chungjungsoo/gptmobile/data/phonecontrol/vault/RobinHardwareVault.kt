package dev.chungjungsoo.gptmobile.data.phonecontrol.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import dev.chungjungsoo.gptmobile.data.security.SecretVaultException
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class RobinHardwareVault @Inject constructor(
    @ApplicationContext context: Context
) : SecretVault {
    private val directory = File(context.noBackupFilesDir, DIRECTORY)

    override suspend fun put(secretRef: String, secret: ByteArray) = withContext(Dispatchers.IO) {
        require(SECRET_REF.matches(secretRef)) { "Invalid credential reference." }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(secretRef.encodeToByteArray())
            val ciphertext = cipher.doFinal(secret)
            val iv = cipher.iv
            directory.mkdirs()
            val payload = ByteBuffer.allocate(4 + iv.size + ciphertext.size)
                .putInt(iv.size)
                .put(iv)
                .put(ciphertext)
                .array()
            File(directory, "$secretRef.vault").writeBytes(payload)
            payload.fill(0)
            ciphertext.fill(0)
            iv.fill(0)
        } catch (error: UserNotAuthenticatedException) {
            throw SecretVaultException("Vault is locked. Unlock with biometric to continue.", error)
        } catch (error: SecretVaultException) {
            throw error
        } catch (error: Exception) {
            throw SecretVaultException("Unable to store the credential.", error)
        }
    }

    override suspend fun read(secretRef: String): ByteArray? = withContext(Dispatchers.IO) {
        require(SECRET_REF.matches(secretRef)) { "Invalid credential reference." }
        val file = File(directory, "$secretRef.vault")
        if (!file.exists()) return@withContext null
        try {
            val record = file.readBytes()
            val buffer = ByteBuffer.wrap(record)
            val ivSize = buffer.int
            if (ivSize !in 12..32 || buffer.remaining() <= ivSize) {
                throw SecretVaultException("Credential record is invalid.")
            }
            val iv = ByteArray(ivSize)
            buffer.get(iv)
            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.updateAAD(secretRef.encodeToByteArray())
            cipher.doFinal(ciphertext).also {
                record.fill(0)
                iv.fill(0)
                ciphertext.fill(0)
            }
        } catch (error: UserNotAuthenticatedException) {
            throw SecretVaultException("Vault is locked. Unlock with biometric to continue.", error)
        } catch (error: SecretVaultException) {
            throw error
        } catch (error: Exception) {
            throw SecretVaultException("Unable to read the credential.", error)
        }
    }

    override suspend fun delete(secretRef: String) = withContext(Dispatchers.IO) {
        require(SECRET_REF.matches(secretRef)) { "Invalid credential reference." }
        File(directory, "$secretRef.vault").delete()
        Unit
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(AUTH_WINDOW_SECONDS, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val DIRECTORY = "robin-vault"
        private const val KEY_ALIAS = "robin-phone-vault-v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AUTH_WINDOW_SECONDS = 30
        private val SECRET_REF = Regex("[A-Za-z0-9_-]{1,128}")
    }
}
