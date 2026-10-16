package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.chungjungsoo.gptmobile.R

/**
 * Product name. The only source is [R.string.app_name].
 */
object Brand {
    @Composable
    fun name(): String = stringResource(R.string.app_name)

    fun name(context: Context): String = context.getString(R.string.app_name)
}
