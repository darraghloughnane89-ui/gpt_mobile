package dev.chungjungsoo.gptmobile.data.gguf

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.chungjungsoo.gptmobile.di.GgufPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class GgufPreferenceStore @Inject constructor(
    @param:GgufPreferences private val dataStore: DataStore<Preferences>
) : GgufDefaults {
    override suspend fun getDefaultModelId(): String? = defaultModelId().first()

    override suspend fun setDefaultModelId(id: String?) {
        dataStore.edit { preferences ->
            if (id.isNullOrBlank()) {
                preferences.remove(KEY_DEFAULT_MODEL_ID)
            } else {
                preferences[KEY_DEFAULT_MODEL_ID] = id
            }
        }
    }

    fun defaultModelId(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_DEFAULT_MODEL_ID]?.takeIf { it.isNotBlank() }
    }

    private companion object {
        val KEY_DEFAULT_MODEL_ID = stringPreferencesKey("gguf_default_model_id")
    }
}
