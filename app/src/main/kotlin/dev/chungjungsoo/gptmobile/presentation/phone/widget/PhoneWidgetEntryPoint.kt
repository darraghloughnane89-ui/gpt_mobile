package dev.chungjungsoo.gptmobile.presentation.phone.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.widget.WidgetBoardRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PhoneWidgetEntryPoint {
    fun chatRepository(): ChatRepository
    fun widgetBoardRepository(): WidgetBoardRepository
}

internal fun widgetEntryPoint(context: android.content.Context): PhoneWidgetEntryPoint = EntryPointAccessors.fromApplication(
    context.applicationContext,
    PhoneWidgetEntryPoint::class.java
)
