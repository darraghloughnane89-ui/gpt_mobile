package dev.chungjungsoo.gptmobile.data.hermes

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface HermesSettingsStore {
    val settings: Flow<HermesSettings>

    suspend fun current(): HermesSettings

    suspend fun update(transform: (HermesSettings) -> HermesSettings)
}

private val Context.hermesDataStore: DataStore<Preferences> by preferencesDataStore(name = "hermes_settings")

@Singleton
class DataStoreHermesSettingsStore @Inject constructor(
    @ApplicationContext context: Context
) : HermesSettingsStore {
    private val dataStore = context.hermesDataStore

    override val settings: Flow<HermesSettings> = dataStore.data.map { prefs -> prefs.toSettings() }

    override suspend fun current(): HermesSettings = settings.first()

    override suspend fun update(transform: (HermesSettings) -> HermesSettings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[ENDPOINT] = next.endpoint
            prefs[PROFILE] = next.profile
            prefs[ROUTE] = next.routeMode.name
            prefs[MODEL] = next.modelId
            prefs[STREAMING] = next.streaming
            prefs[CHAT_MODEL] = next.chatModelEnabled
        }
    }

    private fun Preferences.toSettings(): HermesSettings = HermesSettings(
        endpoint = this[ENDPOINT].orEmpty(),
        profile = this[PROFILE]?.takeIf { it.isNotBlank() } ?: HermesDefaults.PROFILE,
        routeMode = this[ROUTE]?.let { name ->
            HermesRouteMode.entries.firstOrNull { it.name == name }
        } ?: HermesRouteMode.UNKNOWN,
        modelId = this[MODEL].orEmpty(),
        streaming = this[STREAMING] ?: true,
        chatModelEnabled = this[CHAT_MODEL] ?: false
    )

    private companion object {
        val ENDPOINT = stringPreferencesKey("endpoint")
        val PROFILE = stringPreferencesKey("profile")
        val ROUTE = stringPreferencesKey("route_mode")
        val MODEL = stringPreferencesKey("model_id")
        val STREAMING = booleanPreferencesKey("streaming")
        val CHAT_MODEL = booleanPreferencesKey("chat_model_enabled")
    }
}

class MemoryHermesSettingsStore(
    initial: HermesSettings = HermesSettings()
) : HermesSettingsStore {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<HermesSettings> = state

    override suspend fun current(): HermesSettings = state.value

    override suspend fun update(transform: (HermesSettings) -> HermesSettings) {
        state.value = transform(state.value)
    }
}
