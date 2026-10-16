package dev.chungjungsoo.gptmobile.data.routine

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.routineTemplatePreferences by preferencesDataStore(name = "routine_template_preferences")

interface RoutineTemplateSettings {
    fun observe(): Flow<Map<String, RoutineTemplateOverride>>
    suspend fun save(id: String, override: RoutineTemplateOverride)
}

@Serializable
private data class StoredTemplateOverride(
    val prompt: String? = null,
    val hour: Int? = null,
    val minute: Int? = null,
    val dayOfWeek: Int? = null,
    val location: String? = null
)

@Singleton
class DataStoreRoutineTemplateSettings @Inject constructor(
    @param:ApplicationContext private val context: Context
) : RoutineTemplateSettings {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun observe(): Flow<Map<String, RoutineTemplateOverride>> = context.routineTemplatePreferences.data.map { prefs ->
        RoutineTemplates.all.mapNotNull { template ->
            val raw = prefs[stringPreferencesKey(template.id)] ?: return@mapNotNull null
            val stored = runCatching { json.decodeFromString(StoredTemplateOverride.serializer(), raw) }.getOrNull() ?: return@mapNotNull null
            template.id to RoutineTemplateOverride(
                prompt = stored.prompt,
                hour = stored.hour,
                minute = stored.minute,
                dayOfWeek = stored.dayOfWeek,
                location = stored.location
            )
        }.toMap()
    }

    override suspend fun save(id: String, override: RoutineTemplateOverride) {
        val stored = StoredTemplateOverride(
            prompt = override.prompt,
            hour = override.hour,
            minute = override.minute,
            dayOfWeek = override.dayOfWeek,
            location = override.location
        )
        context.routineTemplatePreferences.edit { prefs ->
            prefs[stringPreferencesKey(id)] = json.encodeToString(StoredTemplateOverride.serializer(), stored)
        }
    }
}
