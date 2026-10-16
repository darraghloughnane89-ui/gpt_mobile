package dev.chungjungsoo.gptmobile.presentation.ui.voice

import dev.chungjungsoo.gptmobile.data.voice.MemoryVoiceKeyStore
import dev.chungjungsoo.gptmobile.data.voice.MemoryVoicePreferences
import dev.chungjungsoo.gptmobile.data.voice.VoiceBackendStatus
import dev.chungjungsoo.gptmobile.data.voice.VoiceSpeaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSettingsViewModelTest {
    @Test
    fun `saving a key switches the status to MiMo and hides the secret`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val keys = MemoryVoiceKeyStore()
            val speaker = RecordingSpeaker()
            val viewModel = VoiceSettingsViewModel(MemoryVoicePreferences(), keys, speaker)
            viewModel.setUseMiMo(true)
            viewModel.saveKey("  tp-secret-key-1234  ")
            assertEquals(VoiceBackendStatus.MIMO, viewModel.state.value.status)
            assertEquals("1234", viewModel.state.value.keyPreview)
            assertFalse(viewModel.state.value.keyPreview.contains("tp-secret"))
            assertTrue(viewModel.state.value.hasKey)
            viewModel.preview("Hello")
            assertEquals(listOf("Hello"), speaker.spoken)
            assertFalse(viewModel.state.value.playing)
            viewModel.clearKey()
            assertEquals(VoiceBackendStatus.DEVICE_MISSING_KEY, viewModel.state.value.status)
            assertNull(viewModel.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `a blank key is not stored`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val keys = MemoryVoiceKeyStore()
            val viewModel = VoiceSettingsViewModel(MemoryVoicePreferences(), keys, RecordingSpeaker())
            viewModel.saveKey("   ")
            assertFalse(viewModel.state.value.hasKey)
            assertEquals(VoiceBackendStatus.DEVICE_BY_CHOICE, viewModel.state.value.status)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class RecordingSpeaker : VoiceSpeaker {
    val spoken = mutableListOf<String>()

    override suspend fun speak(utteranceId: String, text: String) {
        spoken += text
    }

    override suspend fun stop() = Unit
}
