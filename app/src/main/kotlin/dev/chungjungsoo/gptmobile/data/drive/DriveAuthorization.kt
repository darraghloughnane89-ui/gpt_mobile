package dev.chungjungsoo.gptmobile.data.drive

import android.app.PendingIntent

sealed class DriveAuthorization {
    data class Ready(val accessToken: String) : DriveAuthorization()
    data object NeedsConsent : DriveAuthorization()
    data class NotConfigured(val reason: String) : DriveAuthorization()
    data class Failed(val reason: String) : DriveAuthorization()
}

interface DriveAuthorizer {
    suspend fun authorize(): DriveAuthorization
    fun consentIntent(): PendingIntent? = null
}

interface DriveAppDataStore {
    suspend fun findBackup(accessToken: String): String?
    suspend fun upload(accessToken: String, existingFileId: String?, bytes: ByteArray): String
    suspend fun download(accessToken: String, fileId: String): ByteArray
}
