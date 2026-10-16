package dev.chungjungsoo.gptmobile.data.hermes

data class HermesPending(
    val threadId: String,
    val user: HermesTurn,
    val assistant: HermesTurn
)

object HermesThreadReducer {
    fun enqueue(
        snapshot: HermesThreadSnapshot,
        request: HermesHandoffRequest,
        newId: () -> String,
        now: Long
    ): HermesThreadSnapshot {
        val active = snapshot.threads.firstOrNull { it.id == snapshot.activeThreadId }
            ?: HermesThread(id = newId(), title = "Hermes")
        val user = HermesTurn(
            id = newId(),
            role = HermesRole.USER,
            kind = request.kind,
            text = request.text.trim(),
            status = HermesDeliveryStatus.DELIVERED,
            attachments = request.attachments,
            createdAtEpochMs = now
        )
        val assistant = HermesTurn(
            id = newId(),
            role = HermesRole.ASSISTANT,
            kind = request.kind,
            status = HermesDeliveryStatus.QUEUED,
            idempotencyKey = newId(),
            createdAtEpochMs = now
        )
        val title = active.title.takeIf { it.isNotBlank() && active.turns.isNotEmpty() }
            ?: user.text.lineSequence().firstOrNull { it.isNotBlank() }?.take(80)
            ?: "Hermes"
        val updated = active.copy(title = title, turns = active.turns + user + assistant)
        val threads = snapshot.threads.filterNot { it.id == updated.id } + updated
        return snapshot.copy(threads = threads, activeThreadId = updated.id)
    }

    fun startNewThread(snapshot: HermesThreadSnapshot, id: String): HermesThreadSnapshot {
        val active = snapshot.threads.firstOrNull { it.id == snapshot.activeThreadId }
        if (active != null && active.turns.isEmpty()) return snapshot
        val thread = HermesThread(id = id, title = "Hermes")
        return snapshot.copy(threads = snapshot.threads + thread, activeThreadId = id)
    }

    fun recover(snapshot: HermesThreadSnapshot): HermesThreadSnapshot = snapshot.copy(
        threads = snapshot.threads.map { thread ->
            thread.copy(
                turns = thread.turns.map { turn ->
                    when {
                        turn.status == HermesDeliveryStatus.SENDING && !turn.accepted ->
                            turn.copy(status = HermesDeliveryStatus.QUEUED)
                        turn.status == HermesDeliveryStatus.SENDING || turn.status == HermesDeliveryStatus.STREAMING ->
                            turn.copy(status = HermesDeliveryStatus.INTERRUPTED)
                        else -> turn
                    }
                }
            )
        }
    )

    fun nextPending(snapshot: HermesThreadSnapshot): HermesPending? {
        snapshot.threads.forEach { thread ->
            thread.turns.forEachIndexed { index, turn ->
                if (turn.role == HermesRole.ASSISTANT && turn.status == HermesDeliveryStatus.QUEUED && index > 0) {
                    val user = thread.turns[index - 1]
                    if (user.role == HermesRole.USER) {
                        return HermesPending(thread.id, user, turn)
                    }
                }
            }
        }
        return null
    }

    fun markSending(snapshot: HermesThreadSnapshot, assistantId: String): HermesThreadSnapshot = updateTurn(snapshot, assistantId) {
        it.copy(status = HermesDeliveryStatus.SENDING)
    }

    fun markAccepted(snapshot: HermesThreadSnapshot, assistantId: String): HermesThreadSnapshot = updateTurn(snapshot, assistantId) {
        it.copy(status = HermesDeliveryStatus.SENDING, accepted = true)
    }

    fun markQueued(snapshot: HermesThreadSnapshot, assistantId: String): HermesThreadSnapshot = updateTurn(snapshot, assistantId) {
        it.copy(status = HermesDeliveryStatus.QUEUED)
    }

    fun markFailed(
        snapshot: HermesThreadSnapshot,
        assistantId: String,
        message: String
    ): HermesThreadSnapshot = updateTurn(snapshot, assistantId) {
        it.copy(status = HermesDeliveryStatus.FAILED, error = message.take(500))
    }

    fun markInterrupted(snapshot: HermesThreadSnapshot, assistantId: String): HermesThreadSnapshot = updateTurn(snapshot, assistantId) {
        it.copy(status = HermesDeliveryStatus.INTERRUPTED, error = it.error ?: "The reply stopped before Hermes finished.")
    }

    fun retry(snapshot: HermesThreadSnapshot, assistantId: String, newKey: String): HermesThreadSnapshot = updateTurn(snapshot, assistantId) { turn ->
        if (turn.status != HermesDeliveryStatus.FAILED && turn.status != HermesDeliveryStatus.INTERRUPTED) {
            turn
        } else {
            turn.copy(
                status = HermesDeliveryStatus.QUEUED,
                error = null,
                accepted = false,
                idempotencyKey = if (turn.accepted) newKey else turn.idempotencyKey.ifBlank { newKey },
                text = if (turn.accepted) "" else turn.text,
                toolNotes = if (turn.accepted) emptyList() else turn.toolNotes
            )
        }
    }

    fun applyEvent(
        snapshot: HermesThreadSnapshot,
        assistantId: String,
        event: HermesStreamEvent
    ): HermesThreadSnapshot = updateTurn(snapshot, assistantId) { turn ->
        when (event) {
            is HermesStreamEvent.Text -> turn.copy(
                text = (turn.text + event.text).take(200_000),
                status = HermesDeliveryStatus.STREAMING,
                accepted = true,
                error = null
            )
            is HermesStreamEvent.Tool -> {
                val notes = if (event.label in turn.toolNotes) turn.toolNotes else (turn.toolNotes + event.label).takeLast(12)
                turn.copy(toolNotes = notes, status = HermesDeliveryStatus.STREAMING, accepted = true)
            }
            is HermesStreamEvent.Completed -> turn.copy(
                text = event.output?.takeIf { it.isNotBlank() } ?: turn.text,
                status = HermesDeliveryStatus.DELIVERED,
                accepted = true,
                error = null
            )
            is HermesStreamEvent.Failed -> turn.copy(
                status = HermesDeliveryStatus.FAILED,
                accepted = true,
                error = event.message.take(500)
            )
        }
    }

    fun priorHistory(snapshot: HermesThreadSnapshot, pending: HermesPending): List<Pair<String, String>> {
        val thread = snapshot.threads.firstOrNull { it.id == pending.threadId } ?: return emptyList()
        val userIndex = thread.turns.indexOfFirst { it.id == pending.user.id }
        if (userIndex <= 0) return emptyList()
        return thread.turns.take(userIndex).mapNotNull { turn ->
            val text = turn.text.trim()
            if (text.isEmpty()) return@mapNotNull null
            when (turn.role) {
                HermesRole.USER -> "user" to text
                HermesRole.ASSISTANT -> if (turn.status == HermesDeliveryStatus.DELIVERED) "assistant" to text else null
            }
        }
    }

    private fun updateTurn(
        snapshot: HermesThreadSnapshot,
        turnId: String,
        block: (HermesTurn) -> HermesTurn
    ): HermesThreadSnapshot = snapshot.copy(
        threads = snapshot.threads.map { thread ->
            if (thread.turns.none { it.id == turnId }) {
                thread
            } else {
                thread.copy(turns = thread.turns.map { turn -> if (turn.id == turnId) block(turn) else turn })
            }
        }
    )
}
