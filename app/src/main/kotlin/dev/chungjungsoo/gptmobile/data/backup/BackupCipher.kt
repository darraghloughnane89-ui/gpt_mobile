package dev.chungjungsoo.gptmobile.data.backup

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BackupCipherException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCipher {
    const val MAGIC = "GPMB"
    private const val VERSION: Byte = 1
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        require(key.size == 32) { "Backup key must be 32 bytes." }
        val iv = ByteArray(IV_BYTES)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return MAGIC.encodeToByteArray() + byteArrayOf(VERSION) + iv + ciphertext
    }

    fun decrypt(key: ByteArray, payload: ByteArray): ByteArray {
        val header = MAGIC.length + 1 + IV_BYTES
        if (key.size != 32 || payload.size < header + TAG_BITS / 8) {
            throw BackupCipherException("Backup file is not a GPT Mobile archive.")
        }
        val magic = payload.copyOfRange(0, MAGIC.length).decodeToString()
        if (magic != MAGIC || payload[MAGIC.length] != VERSION) {
            throw BackupCipherException("Backup file is not a GPT Mobile archive.")
        }
        val ivStart = MAGIC.length + 1
        val iv = payload.copyOfRange(ivStart, ivStart + IV_BYTES)
        val ciphertext = payload.copyOfRange(ivStart + IV_BYTES, payload.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (error: GeneralSecurityException) {
            throw BackupCipherException(
                "The backup could not be decrypted on this device. Export JSON from the phone that created it.",
                error
            )
        }
    }
}
