package dev.chungjungsoo.gptmobile.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.portable.FilePortableLibraryStore
import dev.chungjungsoo.gptmobile.data.portable.PortableLibraryStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PortableModule {
    @Binds
    @Singleton
    abstract fun bindPortableLibrary(store: FilePortableLibraryStore): PortableLibraryStore
}
