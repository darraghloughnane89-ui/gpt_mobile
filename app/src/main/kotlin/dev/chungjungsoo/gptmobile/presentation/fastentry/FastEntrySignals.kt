package dev.chungjungsoo.gptmobile.presentation.fastentry

import android.content.Intent
import dev.chungjungsoo.gptmobile.data.fastentry.FastEntryPlanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object FastEntrySignals {
    private val requestsState = MutableStateFlow<FastEntryPlanner.Request?>(null)
    val requests = requestsState.asStateFlow()

    fun publish(intent: Intent?) {
        val request = intent?.toFastEntryRequest() ?: return
        requestsState.value = request.copy(id = System.nanoTime())
    }

    fun consume() {
        requestsState.value = null
    }
}
