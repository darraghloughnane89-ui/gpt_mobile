package dev.chungjungsoo.gptmobile.presentation.fastentry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.fastentry.FastEntryPlanner
import dev.chungjungsoo.gptmobile.presentation.common.LocalDynamicTheme
import dev.chungjungsoo.gptmobile.presentation.common.LocalThemeMode
import dev.chungjungsoo.gptmobile.presentation.common.ThemeLoadState
import dev.chungjungsoo.gptmobile.presentation.common.ThemeSettingProvider
import dev.chungjungsoo.gptmobile.presentation.common.ThemeViewModel
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme

@AndroidEntryPoint
class QuickAskActivity : ComponentActivity() {
    private val quickAskViewModel: QuickAskViewModel by viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()
    private var sheetTitle by mutableStateOf("")
    private var showJumps by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyChrome(intent)
        if (savedInstanceState == null) {
            val request = intent.toFastEntryRequest()
            quickAskViewModel.bindLaunch(
                selection = request?.selection,
                canReplaceSelection = request.canReplaceSelection()
            )
        }
        setContent {
            val themeLoadState by themeViewModel.loadState.collectAsStateWithLifecycle()
            val ready = themeLoadState != ThemeLoadState.LOADING
            ThemeSettingProvider(themeViewModel) {
                if (ready) {
                    GPTMobileTheme(
                        dynamicTheme = LocalDynamicTheme.current,
                        themeMode = LocalThemeMode.current
                    ) {
                        QuickAskSheet(
                            title = sheetTitle,
                            showJumps = showJumps,
                            viewModel = quickAskViewModel,
                            onDismiss = { finish() },
                            onContinue = { chatId, platforms ->
                                startActivity(FastEntryIntents.openChat(this, chatId, platforms))
                                finish()
                            },
                            onNewChat = {
                                startActivity(FastEntryIntents.newChat(this))
                                finish()
                            },
                            onVoice = {
                                startActivity(FastEntryIntents.voice(this))
                                finish()
                            },
                            onReplace = { answer ->
                                setResult(
                                    RESULT_OK,
                                    Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, answer)
                                )
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyChrome(intent)
        val request = intent.toFastEntryRequest()
        quickAskViewModel.offerSelection(
            selection = request?.selection,
            canReplaceSelection = request.canReplaceSelection()
        )
    }

    private fun applyChrome(intent: Intent) {
        sheetTitle = when (intent.action) {
            FastEntryPlanner.ACTION_PROCESS_TEXT -> getString(R.string.ask_robin)
            else -> getString(R.string.shortcut_quick_ask)
        }
        showJumps = intent.action != FastEntryPlanner.ACTION_PROCESS_TEXT
    }
}

private fun FastEntryPlanner.Request?.canReplaceSelection(): Boolean =
    this?.action == FastEntryPlanner.Action.PROCESS_TEXT && !selectionReadOnly
