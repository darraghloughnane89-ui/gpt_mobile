package dev.chungjungsoo.gptmobile.presentation.phone.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity

internal fun phoneIntent(context: Context, uri: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
    component = ComponentName(context, MainActivity::class.java)
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}

internal fun openAppIntent(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}
