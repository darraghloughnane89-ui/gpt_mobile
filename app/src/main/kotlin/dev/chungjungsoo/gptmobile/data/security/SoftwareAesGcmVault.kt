package dev.chungjungsoo.gptmobile.data.security

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * AES-GCM vault backed by a software key. Unit tests use it as a fake keystore.
 * Production devices use [AndroidSecretVault].
 */
class SoftwareAesGcmVault(
    private val directory: File,
    private val key: SecretKey = generateKey()
) : SecretVault {
    private val mutex = Mutex()
    private val metadata = SecretMetadataStore(directory)
    private val random = SecureRandom()

    override suspend fun put(secretRef: String, secret: ByteArray) = withContext(Dispatchers.IO) {
        require(SECRET_REF.matches(secretRef)) { "Invalid credential reference." }
        require(secret.size <= MAX_SECRET_BYTES) { "Secret exceeds the vault size limit." }
        mutex.withLock {
            directory.mkdirs()
            val iv = ByteArray(IV_BYTES).also(random::nextBytes)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(secretRef.encodeToByteArray())
            val ciphertext = cipher.doFinal(secret)
            File(directory, "$secretRef.vault").writeBytes(iv + ciphertext)
            iv.fill(0)
            ciphertext.fill(0)
        }
    }

    override suspend fun read(secretRef: String): ByteArray? = withContext(Dispatchers.IO) {
        require(SECRET_REF.matches(secretRef)) { "Invalid credential reference." }
        mutex.withLock {
            val file = File(directory, "$secretRef.vault")
            if (!file.isFile) return@withLock null
            val record = file.readBytes()
            try {
                if (record.size <= IV_BYTES) throw SecretVaultException("Credential record is invalid.")
                val iv = record.copyOfRange(0, IV_BYTES)
                val ciphertext = record.copyOfRange(IV_BYTES, record.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                cipher.updateAAD(secretRef.encodeToByteArray())
                cipher.doFinal(ciphertext)
            } catch (error: SecretVaultException) {
                throw error
            } catch (error: Exception) {
                throw SecretVaultException("Unable to read the credential.", error)
            } finally {
                record.fill(0)
            }
        }
    }

    override suspend fun delete(secretRef: String) = withContext(Dispatchers.IO) {
        require(SECRET_REF.matches(secretRef)) { "Invalid credential reference." }
        mutex.withLock {
            File(directory, "$secretRef.vault").delete()
            metadata.delete(secretRef)
        }
    }

    override suspend fun store(entry: SecretEntry, secret: ByteArray): SecretEntry {
        put(entry.id, secret)
        val stored = entry.copy(
            masked = SecretMask.of(secret),
            updatedAtEpochMs = System.currentTimeMillis()
        )
        metadata.write(stored)
        return stored
    }

    override suspend fun entries(): List<SecretEntry> = withContext(Dispatchers.IO) {
        mutex.withLock { metadata.list() }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val MAX_SECRET_BYTES = 64 * 1024
        private val SECRET_REF = Regex("[A-Za-z0-9_-]{1,128}")

        fun generateKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }
}
