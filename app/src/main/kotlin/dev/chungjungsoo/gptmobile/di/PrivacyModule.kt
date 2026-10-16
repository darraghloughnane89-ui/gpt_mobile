package dev.chungjungsoo.gptmobile.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.memory.MemoryPromptSource
import dev.chungjungsoo.gptmobile.data.memory.MemorySuggestionBus
import dev.chungjungsoo.gptmobile.data.memory.MemorySuggestionSink
import dev.chungjungsoo.gptmobile.data.memory.RoomMemoryPromptSource
import dev.chungjungsoo.gptmobile.data.usage.RoomUsageRecorder
import dev.chungjungsoo.gptmobile.data.usage.UsageRecorder
import javax.inject.Singleton

private const val PRIVACY_PREF_FILE = "privacy"

@Module
@InstallIn(SingletonComponent::class)
object PrivacyDataStoreModule {
    @Provides
    @Singleton
    @PrivacyPreferencesStore
    fun providePrivacyDataStore(@ApplicationContext applicationContext: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(
            produceNewData = { emptyPreferences() }
        ),
        produceFile = { applicationContext.preferencesDataStoreFile(PRIVACY_PREF_FILE) }
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PrivacyBindingModule {
    @Binds
    abstract fun bindMemoryPromptSource(impl: RoomMemoryPromptSource): MemoryPromptSource

    @Binds
    abstract fun bindUsageRecorder(impl: RoomUsageRecorder): UsageRecorder

    @Binds
    abstract fun bindMemorySuggestionSink(impl: MemorySuggestionBus): MemorySuggestionSink
}
