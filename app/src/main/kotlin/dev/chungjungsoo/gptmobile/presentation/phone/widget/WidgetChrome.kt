package dev.chungjungsoo.gptmobile.presentation.phone.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

@Composable
internal fun WidgetSurface(
    title: String,
    onClick: Intent? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    HomeWidgetTheme {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .thenClick(onClick)
                .padding(12.dp)
        ) {
            Text(
                text = title,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold
                )
            )
            content()
        }
    }
}

@Composable
internal fun WidgetMessage(text: String) {
    Text(
        text = text,
        maxLines = 4,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
}

private fun GlanceModifier.thenClick(intent: Intent?): GlanceModifier = if (intent == null) {
    this
} else {
    clickable(actionStartActivity(intent))
}
