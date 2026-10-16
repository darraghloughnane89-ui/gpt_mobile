package dev.chungjungsoo.gptmobile.data.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

fun interface MicrophoneAccess {
    fun granted(): Boolean
}

@Singleton
class AndroidMicrophoneAccess @Inject constructor(
    @param:ApplicationContext private val context: Context
) : MicrophoneAccess {
    override fun granted(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}
