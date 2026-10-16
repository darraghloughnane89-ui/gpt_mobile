package dev.chungjungsoo.gptmobile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.hermes.AppHermesAttachmentRoots
import dev.chungjungsoo.gptmobile.data.hermes.DataStoreHermesSettingsStore
import dev.chungjungsoo.gptmobile.data.hermes.FileHermesThreadStore
import dev.chungjungsoo.gptmobile.data.hermes.HermesAttachmentRoots
import dev.chungjungsoo.gptmobile.data.hermes.HermesCredentialStore
import dev.chungjungsoo.gptmobile.data.hermes.HermesSettingsStore
import dev.chungjungsoo.gptmobile.data.hermes.HermesThreadStore
import dev.chungjungsoo.gptmobile.data.hermes.HermesTransport
import dev.chungjungsoo.gptmobile.data.hermes.HermesWorkScheduler
import dev.chungjungsoo.gptmobile.data.hermes.OkHttpHermesTransport
import dev.chungjungsoo.gptmobile.data.hermes.VaultHermesCredentialStore
import dev.chungjungsoo.gptmobile.data.hermes.WorkManagerHermesScheduler
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HermesModule {
    @Provides
    @Singleton
    fun provideHermesThreadStore(@ApplicationContext context: Context): HermesThreadStore = FileHermesThreadStore(
        File(context.noBackupFilesDir, "hermes/threads.json")
    )

    @Provides
    @Singleton
    fun provideHermesTransport(transport: OkHttpHermesTransport): HermesTransport = transport

    @Provides
    @Singleton
    fun provideHermesSettingsStore(store: DataStoreHermesSettingsStore): HermesSettingsStore = store

    @Provides
    @Singleton
    fun provideHermesCredentialStore(store: VaultHermesCredentialStore): HermesCredentialStore = store

    @Provides
    @Singleton
    fun provideHermesWorkScheduler(scheduler: WorkManagerHermesScheduler): HermesWorkScheduler = scheduler

    @Provides
    @Singleton
    fun provideHermesAttachmentRoots(roots: AppHermesAttachmentRoots): HermesAttachmentRoots = roots
}
