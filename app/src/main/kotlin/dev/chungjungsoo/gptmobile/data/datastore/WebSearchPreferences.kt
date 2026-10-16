package dev.chungjungsoo.gptmobile.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class WebSearchPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    suspend fun isEnabled(): Boolean = dataStore.data.first()[ENABLED] ?: false

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[ENABLED] = enabled }
    }

    suspend fun searxngUrl(): String = dataStore.data.first()[SEARXNG].orEmpty()

    suspend fun setSearxngUrl(url: String) {
        dataStore.edit { preferences -> preferences[SEARXNG] = url.trim() }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("composer_web_search_enabled")
        val SEARXNG = stringPreferencesKey("composer_searxng_url")
    }
}
