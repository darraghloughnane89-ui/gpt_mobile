package dev.chungjungsoo.gptmobile.data.voice

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.handsFreePreferences by preferencesDataStore(name = "hands_free_preferences")

data class HandsFreeSettings(
    val wakeEnabled: Boolean = false,
    val batteryStopPercent: Int = BatteryStopPolicy.DEFAULT_THRESHOLD_PERCENT,
    val chatRoomId: Int = 0,
    val enabledPlatforms: String = ""
)

@Singleton
class HandsFreePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val wakeKey = booleanPreferencesKey("wake_enabled")
    private val batteryKey = intPreferencesKey("battery_stop_percent")
    private val chatKey = intPreferencesKey("last_chat_room_id")
    private val platformsKey = stringPreferencesKey("last_enabled_platforms")

    val settings: Flow<HandsFreeSettings> = context.handsFreePreferences.data.map { prefs ->
        HandsFreeSettings(
            wakeEnabled = prefs[wakeKey] == true,
            batteryStopPercent = prefs[batteryKey] ?: BatteryStopPolicy.DEFAULT_THRESHOLD_PERCENT,
            chatRoomId = prefs[chatKey] ?: 0,
            enabledPlatforms = prefs[platformsKey].orEmpty()
        )
    }

    suspend fun setWakeEnabled(enabled: Boolean) {
        context.handsFreePreferences.edit { prefs -> prefs[wakeKey] = enabled }
    }

    suspend fun rememberSession(chatRoomId: Int, enabledPlatforms: String) {
        context.handsFreePreferences.edit { prefs ->
            prefs[chatKey] = chatRoomId
            prefs[platformsKey] = enabledPlatforms
        }
    }

    suspend fun current(): HandsFreeSettings = settings.first()
}
