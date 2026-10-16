package dev.chungjungsoo.gptmobile.data.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Stored in the preferences file that backup rules already exclude, so a routine
 * summary cannot leave the device through backup or device transfer.
 */
class DataStoreWidgetBoardStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : WidgetBoardStore {
    override suspend fun read(): WidgetBoard {
        val prefs = dataStore.data.first()
        return WidgetBoard(
            pinnedChatIds = WidgetBoardCodec.decodePinned(prefs[PINNED_IDS]),
            showContentWhenLocked = prefs[SHOW_WHEN_LOCKED] ?: false,
            latestRoutine = WidgetBoardCodec.decodeRoutine(prefs[LATEST_ROUTINE])
        )
    }

    override suspend fun writePinned(ids: List<Int>) {
        dataStore.edit { prefs ->
            prefs[PINNED_IDS] = WidgetBoardCodec.encodePinned(ids)
        }
    }

    override suspend fun writeShowContentWhenLocked(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SHOW_WHEN_LOCKED] = enabled
        }
    }

    override suspend fun writeLatestRoutine(result: RoutineResult) {
        dataStore.edit { prefs ->
            prefs[LATEST_ROUTINE] = WidgetBoardCodec.encodeRoutine(result)
        }
    }

    private companion object {
        val PINNED_IDS = stringPreferencesKey("widget_pinned_chat_ids")
        val SHOW_WHEN_LOCKED = booleanPreferencesKey("widget_show_content_when_locked")
        val LATEST_ROUTINE = stringPreferencesKey("widget_latest_routine")
    }
}
