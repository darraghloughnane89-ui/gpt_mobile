package dev.chungjungsoo.gptmobile.data.hermes

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HermesHandoff @Inject constructor(
    private val threads: HermesThreadStore,
    private val settingsStore: HermesSettingsStore,
    private val credentials: HermesCredentialStore,
    private val scheduler: HermesWorkScheduler
) {
    suspend fun submit(request: HermesHandoffRequest): HermesHandoffResult {
        val text = request.text.trim()
        val usable = request.copy(
            text = text.ifBlank { request.attachments.joinToString { it.displayName } }
        )
        threads.update { snapshot ->
            HermesThreadReducer.enqueue(
                snapshot = snapshot,
                request = usable,
                newId = { UUID.randomUUID().toString() },
                now = System.currentTimeMillis()
            )
        }
        scheduler.enqueueDrain()
        val ready = settingsStore.current().endpoint.isNotBlank() && !credentials.read().isNullOrBlank()
        return if (ready) HermesHandoffResult.QUEUED else HermesHandoffResult.QUEUED_NEEDS_SETUP
    }
}

interface HermesWorkScheduler {
    fun enqueueDrain()
}
