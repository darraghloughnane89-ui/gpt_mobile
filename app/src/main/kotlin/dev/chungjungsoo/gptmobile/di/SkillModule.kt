package dev.chungjungsoo.gptmobile.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2
import dev.chungjungsoo.gptmobile.data.instruction.InstructionStack
import dev.chungjungsoo.gptmobile.data.skill.BotSkillLinkStore
import dev.chungjungsoo.gptmobile.data.skill.MemoryWriter
import dev.chungjungsoo.gptmobile.data.skill.PlatformSkillReviewer
import dev.chungjungsoo.gptmobile.data.skill.RoomSkillStore
import dev.chungjungsoo.gptmobile.data.skill.SkillClock
import dev.chungjungsoo.gptmobile.data.skill.SkillDao
import dev.chungjungsoo.gptmobile.data.skill.SkillInstructionProvider
import dev.chungjungsoo.gptmobile.data.skill.SkillRepository
import dev.chungjungsoo.gptmobile.data.skill.SkillReviewer
import dev.chungjungsoo.gptmobile.data.skill.SkillStore
import dev.chungjungsoo.gptmobile.data.skill.StubMemoryWriter
import dev.chungjungsoo.gptmobile.data.skill.SystemSkillClock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SkillModule {
    @Binds
    @Singleton
    abstract fun skillStore(impl: RoomSkillStore): SkillStore

    @Binds
    @Singleton
    abstract fun skillClock(impl: SystemSkillClock): SkillClock

    @Binds
    @Singleton
    abstract fun memoryWriter(impl: StubMemoryWriter): MemoryWriter

    @Binds
    @Singleton
    abstract fun skillReviewer(impl: PlatformSkillReviewer): SkillReviewer

    @Binds
    @Singleton
    abstract fun botSkillLinks(impl: SkillRepository): BotSkillLinkStore

    companion object {
        @Provides
        fun skillDao(database: ChatDatabaseV2): SkillDao = database.skillDao()

        @Provides
        @Singleton
        fun instructionStack(provider: SkillInstructionProvider): InstructionStack = InstructionStack(listOf(provider))
    }
}
