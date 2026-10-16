package dev.chungjungsoo.gptmobile.data.phonecontrol

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CredentialManager

class PasskeyBridge {
    suspend fun create(context: Context, requestJson: String, userConfirmed: Boolean): String {
        if (!userConfirmed || requestJson.isBlank()) return "passkey blocked. Confirmation required."
        return runCatching {
            CredentialManager.create(context).createCredential(
                context,
                CreatePublicKeyCredentialRequest(requestJson)
            )
            "passkey created"
        }.getOrDefault("passkey was not created")
    }
}
