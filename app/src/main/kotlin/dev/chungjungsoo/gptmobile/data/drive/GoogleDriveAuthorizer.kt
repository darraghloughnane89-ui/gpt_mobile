package dev.chungjungsoo.gptmobile.data.drive

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.R
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class GoogleDriveAuthorizer @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DriveAuthorizer {
    private var pendingConsent: PendingIntent? = null

    override fun consentIntent(): PendingIntent? = pendingConsent

    override suspend fun authorize(): DriveAuthorization {
        pendingConsent = null
        if (!playServicesReady()) {
            return DriveAuthorization.NotConfigured(context.getString(R.string.backup_play_services))
        }
        return try {
            withContext(Dispatchers.Main) { requestAuthorization() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mapFailure(error)
        }
    }

    private suspend fun requestAuthorization(): DriveAuthorization {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveBackupSpec.SCOPE)))
            .build()
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        if (result.hasResolution()) {
            val intent = result.pendingIntent
            if (intent == null) {
                return DriveAuthorization.NotConfigured(context.getString(R.string.backup_not_configured))
            }
            pendingConsent = intent
            return DriveAuthorization.NeedsConsent
        }
        val token = result.accessToken
        if (token.isNullOrBlank()) {
            return DriveAuthorization.NotConfigured(context.getString(R.string.backup_not_configured))
        }
        return DriveAuthorization.Ready(token)
    }

    private fun playServicesReady(): Boolean {
        val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        return status == ConnectionResult.SUCCESS
    }

    private fun mapFailure(error: Exception): DriveAuthorization {
        val code = (error as? ApiException)?.statusCode
        return when (code) {
            DriveBackupSpec.DEVELOPER_ERROR, DriveBackupSpec.SIGN_IN_FAILED -> {
                DriveAuthorization.NotConfigured(context.getString(R.string.backup_not_configured))
            }
            null -> DriveAuthorization.Failed(context.getString(R.string.backup_drive_unavailable))
            else -> DriveAuthorization.Failed(context.getString(R.string.backup_drive_status, code))
        }
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
}
