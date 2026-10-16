package dev.chungjungsoo.gptmobile.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.gguf.AvailableStorage
import dev.chungjungsoo.gptmobile.data.gguf.GgufDefaults
import dev.chungjungsoo.gptmobile.data.gguf.GgufDownloadScheduler
import dev.chungjungsoo.gptmobile.data.gguf.GgufPreferenceStore
import dev.chungjungsoo.gptmobile.data.gguf.GgufRemoteMetadataSource
import dev.chungjungsoo.gptmobile.data.gguf.HuggingFaceGgufMetadataClient
import dev.chungjungsoo.gptmobile.data.gguf.StatFsAvailableStorage
import dev.chungjungsoo.gptmobile.data.gguf.WorkManagerGgufDownloadScheduler
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GgufPreferences

@Module
@InstallIn(SingletonComponent::class)
object GgufModule {
    @Provides
    @Singleton
    @GgufPreferences
    fun provideGgufPreferences(@ApplicationContext context: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(
            produceNewData = { emptyPreferences() }
        ),
        produceFile = { context.preferencesDataStoreFile("gguf") }
    )

    @Provides
    @Singleton
    fun provideGgufDefaults(store: GgufPreferenceStore): GgufDefaults = store

    @Provides
    @Singleton
    fun provideGgufDownloadScheduler(scheduler: WorkManagerGgufDownloadScheduler): GgufDownloadScheduler = scheduler

    @Provides
    @Singleton
    fun provideGgufMetadata(client: HuggingFaceGgufMetadataClient): GgufRemoteMetadataSource = client

    @Provides
    @Singleton
    fun provideAvailableStorage(storage: StatFsAvailableStorage): AvailableStorage = storage
}
