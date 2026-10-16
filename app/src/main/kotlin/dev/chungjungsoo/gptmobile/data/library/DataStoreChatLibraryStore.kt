package dev.chungjungsoo.gptmobile.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DataStoreChatLibraryStore(
    private val dataStore: DataStore<Preferences>
) : ChatLibraryStore {
    override fun observe(): Flow<ChatLibraryState> = dataStore.data.map { preferences ->
        ChatLibraryCodec.decode(preferences[LIBRARY_KEY])
    }

    override suspend fun current(): ChatLibraryState = observe().first()

    override suspend fun update(transform: (ChatLibraryState) -> ChatLibraryState): ChatLibraryState {
        var updated = ChatLibraryState()
        dataStore.edit { preferences ->
            updated = transform(ChatLibraryCodec.decode(preferences[LIBRARY_KEY]))
            preferences[LIBRARY_KEY] = ChatLibraryCodec.encode(updated)
        }
        return updated
    }

    private companion object {
        val LIBRARY_KEY = stringPreferencesKey("chat_library_v1")
    }
}
