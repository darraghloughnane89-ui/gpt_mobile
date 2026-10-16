package dev.chungjungsoo.gptmobile.data.plugin

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@Singleton
class PluginOAuthCallbackHub @Inject constructor() {
    private val events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private var last: String? = null

    fun offer(uri: String) {
        if (uri == last) return
        last = uri
        events.tryEmit(uri)
    }

    val callbacks: SharedFlow<String> = events.asSharedFlow()
}

@Singleton
class PluginOAuthCompleter @Inject constructor(
    private val hub: PluginOAuthCallbackHub,
    private val repository: PluginRepository
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            hub.callbacks.collect { uri ->
                runCatching { repository.completeOAuth(uri) }
            }
        }
    }
}
