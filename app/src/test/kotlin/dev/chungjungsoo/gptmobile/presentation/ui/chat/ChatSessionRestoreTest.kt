package dev.chungjungsoo.gptmobile.presentation.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSessionRestoreTest {
    @Test
    fun composerDraftRestoresOnlyNonEmptyText() {
        assertNull(restoreComposerDraft(null))
        assertNull(restoreComposerDraft(""))
        assertEquals("draft", restoreComposerDraft("draft"))
    }

    @Test
    fun stopCancelsTheInFlightSendWithoutCancellingTheNextOne() {
        val gate = StreamCancelGate()
        val first = gate.beginSend()
        gate.cancel()

        assertTrue(gate.wasExplicitlyCancelled(first))

        val second = gate.beginSend()
        assertFalse(gate.wasExplicitlyCancelled(second))
    }

    @Test
    fun stopBeforeTheFirstSendDoesNotCancelThatSend() {
        val gate = StreamCancelGate()
        gate.cancel()
        val send = gate.beginSend()

        assertFalse(gate.wasExplicitlyCancelled(send))
    }
}
