package dev.chungjungsoo.gptmobile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.canvas.CanvasStore
import dev.chungjungsoo.gptmobile.data.canvas.FileCanvasStore
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CanvasModule {
    @Provides
    @Singleton
    fun provideCanvasStore(@ApplicationContext context: Context): CanvasStore =
        FileCanvasStore(File(context.filesDir, "canvases"))
}
