package dev.chungjungsoo.gptmobile.presentation.phone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.chungjungsoo.gptmobile.presentation.common.LocalDynamicTheme
import dev.chungjungsoo.gptmobile.presentation.common.LocalThemeMode
import dev.chungjungsoo.gptmobile.presentation.common.ThemeSettingProvider
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.presentation.ui.home.SelectPlatformDialog
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity

@AndroidEntryPoint
class ShareTargetActivity : ComponentActivity() {
    private val viewModel: ShareTargetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        viewModel.load(intent.toShareSnapshot(contentResolver))
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            ThemeSettingProvider {
                GPTMobileTheme(
                    dynamicTheme = LocalDynamicTheme.current,
                    themeMode = LocalThemeMode.current
                ) {
                    ShareTargetScreen(
                        preview = state.preview,
                        attachmentSummary = attachmentSummary(state.attachmentCount),
                        chats = state.chats,
                        warnings = state.warnings,
                        isLoading = state.isLoading,
                        error = state.error,
                        importing = state.importing,
                        onNewChat = viewModel::onNewChatClicked,
                        onChatSelected = viewModel::onExistingChat,
                        onClose = { finish() }
                    )
                    if (state.showPlatformDialog) {
                        SelectPlatformDialog(
                            platforms = state.platforms,
                            selectedPlatforms = state.selectedPlatforms,
                            onDismissRequest = viewModel::dismissPlatformDialog,
                            onConfirmation = viewModel::confirmPlatforms,
                            onPlatformSelect = viewModel::togglePlatform
                        )
                    }
                    LaunchedEffect(Unit) {
                        viewModel.openChat.collect { request ->
                            startActivity(openChatIntent(request))
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun attachmentSummary(count: Int): String = when (count) {
        0 -> ""
        1 -> getString(dev.chungjungsoo.gptmobile.R.string.share_one_attachment)
        else -> getString(dev.chungjungsoo.gptmobile.R.string.share_attachment_count, count)
    }

    private fun openChatIntent(request: ShareOpenRequest): Intent {
        val uri = if (request.chatId == 0) {
            Uri.parse(dev.chungjungsoo.gptmobile.data.phone.PhoneLinks.NEW_CHAT)
        } else {
            Uri.parse(
                dev.chungjungsoo.gptmobile.data.phone.PhoneLinks.chat(
                    request.chatId,
                    request.enabledPlatforms
                )
            )
        }
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setClass(this@ShareTargetActivity, MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }
}
