package dev.chungjungsoo.gptmobile.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.library.ChatDesk
import dev.chungjungsoo.gptmobile.data.library.ChatLibraryStore
import dev.chungjungsoo.gptmobile.data.library.DataStoreChatLibraryStore
import dev.chungjungsoo.gptmobile.data.library.DefaultChatDesk
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LibraryModule {
    @Provides
    @Singleton
    fun provideChatLibraryStore(dataStore: DataStore<Preferences>): ChatLibraryStore = DataStoreChatLibraryStore(dataStore)

    @Provides
    @Singleton
    fun provideChatDesk(
        store: ChatLibraryStore,
        messageV2Dao: MessageV2Dao
    ): ChatDesk = DefaultChatDesk(store, messageV2Dao)
}
