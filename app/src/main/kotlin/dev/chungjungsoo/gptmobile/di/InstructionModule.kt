package dev.chungjungsoo.gptmobile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.bot.BotCompletionTools
import dev.chungjungsoo.gptmobile.data.bot.BotRepository
import dev.chungjungsoo.gptmobile.data.bot.BotToolResolver
import dev.chungjungsoo.gptmobile.data.bot.NewChatBotHint
import dev.chungjungsoo.gptmobile.data.instruction.InstructionStackComposer
import dev.chungjungsoo.gptmobile.data.instruction.NullProjectInstructionSource
import dev.chungjungsoo.gptmobile.data.instruction.ProjectInstructionSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InstructionModule {
    @Provides
    @Singleton
    fun provideProjectInstructionSource(): ProjectInstructionSource = NullProjectInstructionSource

    @Provides
    @Singleton
    fun provideInstructionStackComposer(
        projectInstructions: ProjectInstructionSource
    ): InstructionStackComposer = InstructionStackComposer(projectInstructions)

    @Provides
    @Singleton
    fun provideNewChatBotHint(botRepository: BotRepository): NewChatBotHint = botRepository

    @Provides
    @Singleton
    fun provideBotCompletionTools(botToolResolver: BotToolResolver): BotCompletionTools = botToolResolver
}
