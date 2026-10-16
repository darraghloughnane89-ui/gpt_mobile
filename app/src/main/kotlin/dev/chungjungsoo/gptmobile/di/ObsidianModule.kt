package dev.chungjungsoo.gptmobile.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.obsidian.FileObsidianSyncIndexStore
import dev.chungjungsoo.gptmobile.data.obsidian.FileObsidianSyncPreferences
import dev.chungjungsoo.gptmobile.data.obsidian.ObsidianSyncIndexStore
import dev.chungjungsoo.gptmobile.data.obsidian.ObsidianSyncPreferences
import dev.chungjungsoo.gptmobile.data.obsidian.VaultSyncFactory
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.BotRepository
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.ChatVaultSource
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.MemoryRepository
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.ProjectRepository
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.RoomChatVaultSource
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.SkillRepository
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.StubBotRepository
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.StubMemoryRepository
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.StubProjectRepository
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.StubSkillRepository
import dev.chungjungsoo.gptmobile.data.obsidian.github.DefaultVaultSyncFactory
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ObsidianModule {
    @Provides
    @Singleton
    fun provideObsidianPreferences(@ApplicationContext context: Context): ObsidianSyncPreferences =
        FileObsidianSyncPreferences(File(context.filesDir, "obsidian_sync_config.json"))

    @Provides
    @Singleton
    fun provideObsidianIndex(@ApplicationContext context: Context): ObsidianSyncIndexStore =
        FileObsidianSyncIndexStore(File(context.filesDir, "obsidian_sync_index.json"))

    @Provides
    @Singleton
    fun provideMemoryRepository(): MemoryRepository = StubMemoryRepository()

    @Provides
    @Singleton
    fun provideSkillRepository(): SkillRepository = StubSkillRepository()

    @Provides
    @Singleton
    fun provideProjectRepository(): ProjectRepository = StubProjectRepository()

    @Provides
    @Singleton
    fun provideBotRepository(): BotRepository = StubBotRepository()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ObsidianBindModule {
    @Binds
    abstract fun bindChatVaultSource(source: RoomChatVaultSource): ChatVaultSource

    @Binds
    abstract fun bindVaultSyncFactory(factory: DefaultVaultSyncFactory): VaultSyncFactory
}
