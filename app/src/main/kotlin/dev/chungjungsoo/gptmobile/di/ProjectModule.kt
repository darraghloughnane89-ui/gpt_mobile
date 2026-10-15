package dev.chungjungsoo.gptmobile.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.project.ProjectChatListSource
import dev.chungjungsoo.gptmobile.data.project.ProjectContextProvider
import dev.chungjungsoo.gptmobile.data.project.ProjectRepository
import dev.chungjungsoo.gptmobile.data.project.ProjectRepositoryImpl
import dev.chungjungsoo.gptmobile.data.project.RoomProjectContextProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProjectModule {
    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds
    abstract fun bindProjectChatListSource(repository: ProjectRepository): ProjectChatListSource

    @Binds
    @Singleton
    abstract fun bindProjectContextProvider(impl: RoomProjectContextProvider): ProjectContextProvider
}
