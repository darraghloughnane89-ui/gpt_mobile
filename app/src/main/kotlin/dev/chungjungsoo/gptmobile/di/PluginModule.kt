package dev.chungjungsoo.gptmobile.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthClient
import dev.chungjungsoo.gptmobile.data.plugin.AgentHandoffExecutor
import dev.chungjungsoo.gptmobile.data.plugin.AgentReplySource
import dev.chungjungsoo.gptmobile.data.plugin.AgentTargetSource
import dev.chungjungsoo.gptmobile.data.plugin.BotDirectory
import dev.chungjungsoo.gptmobile.data.plugin.ChatAgentReplySource
import dev.chungjungsoo.gptmobile.data.plugin.HermesBridge
import dev.chungjungsoo.gptmobile.data.plugin.InstalledAgentTargetSource
import dev.chungjungsoo.gptmobile.data.plugin.PluginHermesBridge
import dev.chungjungsoo.gptmobile.data.plugin.PluginOAuthFlow
import dev.chungjungsoo.gptmobile.data.plugin.PluginToolRegistry
import dev.chungjungsoo.gptmobile.data.plugin.ProjectDirectory
import dev.chungjungsoo.gptmobile.data.plugin.ToolRegistry
import dev.chungjungsoo.gptmobile.data.plugin.UnavailableBotDirectory
import dev.chungjungsoo.gptmobile.data.plugin.UnavailableProjectDirectory
import dev.chungjungsoo.gptmobile.data.routine.RoutineClock
import dev.chungjungsoo.gptmobile.data.routine.RoutineWorkQueue
import dev.chungjungsoo.gptmobile.data.routine.SystemRoutineClock
import dev.chungjungsoo.gptmobile.data.routine.WorkManagerRoutineQueue
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PluginBindModule {
    @Binds
    @Singleton
    abstract fun toolRegistry(impl: PluginToolRegistry): ToolRegistry

    @Binds
    abstract fun bots(impl: UnavailableBotDirectory): BotDirectory

    @Binds
    abstract fun projects(impl: UnavailableProjectDirectory): ProjectDirectory

    @Binds
    abstract fun targets(impl: InstalledAgentTargetSource): AgentTargetSource

    @Binds
    abstract fun replies(impl: ChatAgentReplySource): AgentReplySource

    @Binds
    abstract fun hermes(impl: PluginHermesBridge): HermesBridge

    @Binds
    abstract fun routineClock(impl: SystemRoutineClock): RoutineClock

    @Binds
    abstract fun routineQueue(impl: WorkManagerRoutineQueue): RoutineWorkQueue
}

@Module
@InstallIn(SingletonComponent::class)
object PluginModule {
    @Provides
    @Singleton
    fun pluginOAuthFlow(client: McpOAuthClient): PluginOAuthFlow = PluginOAuthFlow(client)

    @Provides
    @Singleton
    fun agentHandoff(
        targets: AgentTargetSource,
        replies: AgentReplySource,
        hermes: HermesBridge
    ): AgentHandoffExecutor = AgentHandoffExecutor(targets, replies, hermes)
}
