package dev.chungjungsoo.gptmobile.presentation.phone.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.phone.PhoneLinks
import dev.chungjungsoo.gptmobile.data.widget.RoutineWidgetState

class RoutineResultWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadRoutineWidget(context, id)
        provideContent {
            RoutineResultContent(state)
        }
    }
}

@Composable
private fun RoutineResultContent(state: RoutineWidgetState) {
    val context = LocalContext.current
    val openChat = (state as? RoutineWidgetState.Ready)?.chat
    val onClick = if (openChat != null) {
        phoneIntent(context, PhoneLinks.chat(openChat.id, openChat.enabledPlatforms))
    } else {
        openAppIntent(context)
    }
    WidgetSurface(
        title = context.getString(R.string.widget_routine_name),
        onClick = onClick
    ) {
        when (state) {
            RoutineWidgetState.Locked -> WidgetMessage(context.getString(R.string.widget_routine_locked))
            RoutineWidgetState.Empty -> WidgetMessage(context.getString(R.string.widget_routine_empty))
            is RoutineWidgetState.Ready -> {
                Text(
                    text = state.name,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                if (LocalSize.current.height >= 96.dp) {
                    Text(
                        text = state.summary,
                        maxLines = if (LocalSize.current.height >= 160.dp) 6 else 2,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
