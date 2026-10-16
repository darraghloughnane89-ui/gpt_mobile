package dev.chungjungsoo.gptmobile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.prompt.FilePromptLibraryStorage
import dev.chungjungsoo.gptmobile.data.prompt.PromptLibraryRepository
import dev.chungjungsoo.gptmobile.data.prompt.PromptLibraryRepositoryImpl
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PromptLibraryModule {
    @Provides
    @Singleton
    fun providePromptLibraryRepository(
        @ApplicationContext context: Context
    ): PromptLibraryRepository = PromptLibraryRepositoryImpl(
        FilePromptLibraryStorage(File(context.filesDir, PROMPT_LIBRARY_FILE))
    )

    private const val PROMPT_LIBRARY_FILE = "prompt_library.json"
}
