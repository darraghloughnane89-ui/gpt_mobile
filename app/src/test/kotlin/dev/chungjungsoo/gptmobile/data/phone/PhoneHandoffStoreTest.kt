package dev.chungjungsoo.gptmobile.data.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneHandoffStoreTest {
    @Test
    fun consume_matchingId_returnsHandoffOnce() {
        val store = PhoneHandoffStore()
        val handoff = PhoneHandoffStore.Handoff(
            targetChatId = 0,
            text = "Look at this photo",
            launchCamera = true
        )
        store.offer(handoff)

        assertEquals(handoff, store.consume(handoff.id))
        assertNull(store.consume(handoff.id))
        assertNull(store.peek())
    }

    @Test
    fun offer_replacesPreviousHandoff() {
        val store = PhoneHandoffStore()
        val first = PhoneHandoffStore.Handoff(targetChatId = 4, text = "old")
        val second = PhoneHandoffStore.Handoff(targetChatId = 9, text = "new")
        store.offer(first)
        store.offer(second)

        assertNull(store.consume(first.id))
        assertEquals(second, store.consume(second.id))
    }
}
