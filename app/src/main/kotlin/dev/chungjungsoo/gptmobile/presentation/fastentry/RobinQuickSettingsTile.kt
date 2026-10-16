package dev.chungjungsoo.gptmobile.presentation.fastentry

import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.chungjungsoo.gptmobile.R

/**
 * One tile for a new chat or voice. The tap opens the quick-ask sheet,
 * which can ask, start a chat, or open voice without a second tile.
 */
class RobinQuickSettingsTile : TileService() {
    override fun onStartListening() {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_new_chat)
        tile.subtitle = getString(R.string.tile_voice_subtitle)
        tile.state = Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_shortcut_chat)
        tile.updateTile()
    }

    override fun onClick() {
        unlockAndRun {
            val intent = FastEntryIntents.quickAsk(this)
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }
}
