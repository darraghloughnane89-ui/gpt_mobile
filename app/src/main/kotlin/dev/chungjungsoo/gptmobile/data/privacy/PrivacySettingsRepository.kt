package dev.chungjungsoo.gptmobile.data.privacy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import dev.chungjungsoo.gptmobile.di.PrivacyPreferencesStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PrivacySettingsRepository @Inject constructor(
    @param:PrivacyPreferencesStore private val dataStore: DataStore<Preferences>
) {
    val memoryGloballyPaused: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[MEMORY_PAUSED] ?: false
    }
    val appLockEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[APP_LOCK_ENABLED] ?: false
    }
    val lockTimeoutSeconds: Flow<Int> = dataStore.data.map { preferences ->
        preferences[LOCK_TIMEOUT_SECONDS] ?: 0
    }
    val flagSecure: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[FLAG_SECURE] ?: false
    }

    suspend fun setMemoryGloballyPaused(paused: Boolean) {
        dataStore.edit { preferences -> preferences[MEMORY_PAUSED] = paused }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setLockTimeoutSeconds(seconds: Int) {
        dataStore.edit { preferences -> preferences[LOCK_TIMEOUT_SECONDS] = seconds }
    }

    suspend fun setFlagSecure(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[FLAG_SECURE] = enabled }
    }

    private companion object {
        val MEMORY_PAUSED = booleanPreferencesKey("memory_globally_paused")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val LOCK_TIMEOUT_SECONDS = intPreferencesKey("app_lock_timeout_seconds")
        val FLAG_SECURE = booleanPreferencesKey("flag_secure")
    }
}
