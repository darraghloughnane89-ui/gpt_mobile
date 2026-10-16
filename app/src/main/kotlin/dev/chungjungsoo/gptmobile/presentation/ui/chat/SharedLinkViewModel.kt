package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.fetch.SharedLinkInbox
import javax.inject.Inject

@HiltViewModel
class SharedLinkViewModel @Inject constructor(
    inbox: SharedLinkInbox
) : ViewModel() {
    val pendingUrl = inbox.pending
}
