package dev.chungjungsoo.gptmobile.presentation.phone.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.widget.PinnedWidgetState
import dev.chungjungsoo.gptmobile.data.widget.RoutineSummary
import dev.chungjungsoo.gptmobile.data.widget.RoutineWidgetState
import dev.chungjungsoo.gptmobile.data.widget.WidgetBoard
import dev.chungjungsoo.gptmobile.data.widget.WidgetChat
import dev.chungjungsoo.gptmobile.data.widget.WidgetContentPolicy
import dev.chungjungsoo.gptmobile.data.widget.WidgetPresentation
import kotlinx.coroutines.CancellationException

internal const val PINNED_WIDGET_LIMIT = 8

internal suspend fun loadRoutineWidget(context: Context, glanceId: GlanceId): RoutineWidgetState = try {
    val entry = widgetEntryPoint(context)
    val board = entry.widgetBoardRepository().board()
    if (!canReveal(context, glanceId, board)) return RoutineWidgetState.Locked
    val routine = board.latestRoutine ?: return RoutineWidgetState.Empty
    val repository = entry.chatRepository()
    val room = repository.fetchChatListV2().firstOrNull { it.id == routine.chatId } ?: return RoutineWidgetState.Empty
    val untitled = context.getString(R.string.share_untitled_chat)
    val liveSummary = try {
        val messages = repository.fetchMessagesV2(room.id)
        RoutineSummary.fromAssistantTexts(
            messages.filter { it.platformType != null }.map { it.effectiveContent() }
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
    WidgetPresentation.routine(
        deviceLocked = false,
        onKeyguard = false,
        allowWhenLocked = true,
        result = routine,
        liveChat = WidgetChat(
            id = room.id,
            title = room.title.ifBlank { untitled },
            enabledPlatforms = room.enabledPlatform
        ),
        liveSummary = liveSummary
    )
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    RoutineWidgetState.Empty
}

internal suspend fun loadPinnedWidget(context: Context, glanceId: GlanceId): PinnedWidgetState = try {
    val entry = widgetEntryPoint(context)
    val board = entry.widgetBoardRepository().board()
    if (!canReveal(context, glanceId, board)) return PinnedWidgetState.Locked
    val untitled = context.getString(R.string.share_untitled_chat)
    val chats = entry.chatRepository().fetchChatListV2().map { room ->
        WidgetChat(
            id = room.id,
            title = room.title.ifBlank { untitled },
            enabledPlatforms = room.enabledPlatform
        )
    }
    WidgetPresentation.pinned(
        deviceLocked = false,
        onKeyguard = false,
        allowWhenLocked = true,
        chats = chats,
        pinnedIds = board.pinnedChatIds,
        limit = PINNED_WIDGET_LIMIT
    )
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    PinnedWidgetState.Empty
}

private suspend fun canReveal(context: Context, glanceId: GlanceId, board: WidgetBoard): Boolean =
    WidgetContentPolicy.canRevealChatContent(
        deviceLocked = context.isDeviceLocked(),
        onKeyguard = isKeyguardWidget(context, glanceId),
        userAllowsLockedContent = board.showContentWhenLocked
    )

private suspend fun isKeyguardWidget(context: Context, glanceId: GlanceId): Boolean = try {
    val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
    val category = options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1)
    category == AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    false
}
