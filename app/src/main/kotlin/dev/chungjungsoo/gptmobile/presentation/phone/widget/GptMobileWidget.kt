package dev.chungjungsoo.gptmobile.presentation.phone.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.phone.PhoneLinks
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity

data class WidgetChat(
    val id: Int,
    val title: String,
    val enabledPlatforms: List<String>
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PhoneWidgetEntryPoint {
    fun chatRepository(): ChatRepository
}

class GptMobileWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val chats = loadRecentChats(context)
        provideContent {
            GlanceTheme {
                GptMobileWidgetContent(chats)
            }
        }
    }
}

@Composable
private fun GptMobileWidgetContent(chats: List<WidgetChat>) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp)
    ) {
        Text(
            text = context.getString(R.string.app_name),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            androidx.glance.Button(
                text = context.getString(R.string.widget_new_chat),
                onClick = actionStartActivity(phoneIntent(context, PhoneLinks.NEW_CHAT))
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            androidx.glance.Button(
                text = context.getString(R.string.widget_voice),
                onClick = actionStartActivity(phoneIntent(context, PhoneLinks.VOICE))
            )
        }
        if (chats.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = context.getString(R.string.widget_recent),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
            )
            chats.forEach { chat ->
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
        }
    }
}

private suspend fun loadRecentChats(context: Context): List<WidgetChat> = try {
    val repository = EntryPointAccessors.fromApplication(
        context.applicationContext,
        PhoneWidgetEntryPoint::class.java
    ).chatRepository()
    repository.fetchChatListV2().take(RECENT_CHAT_LIMIT).map { room ->
        WidgetChat(
            id = room.id,
            title = room.title.ifBlank { context.getString(R.string.share_untitled_chat) },
            enabledPlatforms = room.enabledPlatform
        )
    }
} catch (_: Exception) {
    emptyList()
}

private fun phoneIntent(context: Context, uri: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
    component = ComponentName(context, MainActivity::class.java)
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}

private const val RECENT_CHAT_LIMIT = 3
