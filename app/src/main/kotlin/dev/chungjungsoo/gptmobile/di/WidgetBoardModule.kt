package dev.chungjungsoo.gptmobile.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.widget.DataStoreWidgetBoardStore
import dev.chungjungsoo.gptmobile.data.widget.WidgetBoardRepository
import dev.chungjungsoo.gptmobile.data.widget.WidgetBoardRepositoryImpl
import dev.chungjungsoo.gptmobile.data.widget.WidgetBoardStore
import dev.chungjungsoo.gptmobile.data.widget.WidgetRefresher
import dev.chungjungsoo.gptmobile.presentation.phone.widget.GlanceWidgetRefresher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WidgetBoardModule {
    @Provides
    @Singleton
    fun provideWidgetBoardStore(dataStore: DataStore<Preferences>): WidgetBoardStore = DataStoreWidgetBoardStore(dataStore)

    @Provides
    @Singleton
    fun provideWidgetRefresher(@ApplicationContext context: Context): WidgetRefresher = GlanceWidgetRefresher(context)

    @Provides
    @Singleton
    fun provideWidgetBoardRepository(
        store: WidgetBoardStore,
        refresher: WidgetRefresher
    ): WidgetBoardRepository = WidgetBoardRepositoryImpl(store, refresher)
}
