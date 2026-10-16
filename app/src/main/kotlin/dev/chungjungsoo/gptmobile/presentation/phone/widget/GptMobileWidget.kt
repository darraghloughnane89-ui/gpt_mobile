package dev.chungjungsoo.gptmobile.presentation.phone.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.phone.PhoneLinks

/**
 * Quick Ask. This is the home-screen widget added with phone integration,
 * extended from a launcher plus recent chats into text and mic actions that
 * open the composer. Recent chats moved to [PinnedChatsWidget].
 */
class GptMobileWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            QuickAskContent()
        }
    }
}

@Composable
private fun QuickAskContent() {
    val context = LocalContext.current
    val compact = LocalSize.current.height < 96.dp
    WidgetSurface(title = context.getString(R.string.widget_quick_ask)) {
        if (!compact) {
            Spacer(modifier = GlanceModifier.height(8.dp))
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .background(GlanceTheme.colors.secondaryContainer)
                    .cornerRadius(20.dp)
                    .clickable(actionStartActivity(phoneIntent(context, PhoneLinks.ask(PhoneLinks.INPUT_TEXT))))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = context.getString(R.string.widget_ask_hint),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer),
                    modifier = GlanceModifier.fillMaxWidth()
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Button(
                text = context.getString(R.string.widget_mic),
                onClick = actionStartActivity(phoneIntent(context, PhoneLinks.ask(PhoneLinks.INPUT_MIC)))
            )
        }
    }
}
