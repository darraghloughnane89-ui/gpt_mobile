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
import dev.chungjungsoo.gptmobile.data.phone.ShareIntentParser
import dev.chungjungsoo.gptmobile.data.portable.PortableFileIntake
import dev.chungjungsoo.gptmobile.data.portable.PortableImportInbox
import dev.chungjungsoo.gptmobile.presentation.common.LocalDynamicTheme
import dev.chungjungsoo.gptmobile.presentation.common.LocalThemeMode
import dev.chungjungsoo.gptmobile.presentation.common.ThemeSettingProvider
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.presentation.ui.home.SelectPlatformDialog
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity
import dev.chungjungsoo.gptmobile.presentation.ui.portable.PortableIntents
import javax.inject.Inject

@AndroidEntryPoint
class ShareTargetActivity : ComponentActivity() {
    private val viewModel: ShareTargetViewModel by viewModels()

    @Inject lateinit var portableInbox: PortableImportInbox

    @Inject lateinit var portableIntake: PortableFileIntake

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (redirectPortable(intent)) {
            finish()
            return
        }
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

    private fun redirectPortable(intent: Intent): Boolean {
        val snapshot = intent.toShareSnapshot(contentResolver)
        val parsed = ShareIntentParser.parse(snapshot)
        val result = when {
            parsed.portableUris.isNotEmpty() -> portableIntake.read(contentResolver, Uri.parse(parsed.portableUris.first()))
            else -> portableIntake.fromShareText(snapshot.extraText) ?: return false
        }
        portableInbox.offer(result)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(PortableIntents.EXTRA_PORTABLE_IMPORT, true)
            }
        )
        return true
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
