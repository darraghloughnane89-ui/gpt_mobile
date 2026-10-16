package dev.chungjungsoo.gptmobile.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.library.ChatLibraryStore
import dev.chungjungsoo.gptmobile.data.library.DataStoreChatLibraryStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LibraryModule {
    @Provides
    @Singleton
    fun provideChatLibraryStore(dataStore: DataStore<Preferences>): ChatLibraryStore = DataStoreChatLibraryStore(dataStore)
}
