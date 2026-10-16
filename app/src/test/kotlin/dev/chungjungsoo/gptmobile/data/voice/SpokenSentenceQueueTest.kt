package dev.chungjungsoo.gptmobile.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenSentenceQueueTest {
    @Test
    fun push_emitsCompleteSentencesAsTheReplyStreamsAndFlushesTheTail() {
        val queue = SpokenSentenceQueue()

        assertEquals(listOf("Hello there."), queue.push("Hello there. How are", flush = false))
        assertEquals(listOf("How are you?"), queue.push("Hello there. How are you? I am", flush = false))
        assertEquals(listOf("I am"), queue.push("Hello there. How are you? I am", flush = true))
    }

    @Test
    fun push_keepsAbbreviationsAndDecimalsTogether() {
        val queue = SpokenSentenceQueue()

        assertEquals(
            listOf("Mr. Smith arrived."),
            queue.push("Mr. Smith arrived. Pi is 3.14 today", flush = false)
        )
        assertEquals(
            listOf("Pi is 3.14 today."),
            queue.push("Mr. Smith arrived. Pi is 3.14 today.", flush = false)
        )
    }

    @Test
    fun push_resetsWhenTheReplyIsReplaced() {
        val queue = SpokenSentenceQueue()
        queue.push("Hello there. Waiting", flush = false)

        assertEquals(listOf("New answer."), queue.push("New answer.", flush = false))
    }
}
