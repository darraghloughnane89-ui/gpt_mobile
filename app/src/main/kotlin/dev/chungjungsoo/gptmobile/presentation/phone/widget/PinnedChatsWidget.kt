package dev.chungjungsoo.gptmobile.presentation.phone.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.phone.PhoneLinks
import dev.chungjungsoo.gptmobile.data.widget.PinnedWidgetState
import dev.chungjungsoo.gptmobile.data.widget.WidgetChat

class PinnedChatsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadPinnedWidget(context, id)
        provideContent {
            PinnedChatsContent(state)
        }
    }
}

@Composable
private fun PinnedChatsContent(state: PinnedWidgetState) {
    val context = LocalContext.current
    WidgetSurface(
        title = context.getString(R.string.widget_pinned_name),
        onClick = if (state is PinnedWidgetState.Ready) null else openAppIntent(context)
    ) {
        when (state) {
            PinnedWidgetState.Locked -> WidgetMessage(context.getString(R.string.widget_pinned_locked))
            PinnedWidgetState.Empty -> WidgetMessage(context.getString(R.string.widget_pinned_empty))
            is PinnedWidgetState.Ready -> {
                val rows = visibleRowCount(state.chats.size)
                state.chats.take(rows).forEach { chat ->
                    PinnedChatRow(chat)
                }
            }
        }
    }
}

@Composable
private fun PinnedChatRow(chat: WidgetChat) {
    val context = LocalContext.current
    Text(
        text = chat.title,
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSurface),
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(
                actionStartActivity(
                    phoneIntent(context, PhoneLinks.chat(chat.id, chat.enabledPlatforms))
                )
            )
    )
}

@Composable
private fun visibleRowCount(available: Int): Int {
    val height = LocalSize.current.height.value
    val rows = ((height - 44f) / 32f).toInt().coerceAtLeast(1)
    return rows.coerceAtMost(available)
}
