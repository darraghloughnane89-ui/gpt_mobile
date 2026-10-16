package dev.chungjungsoo.gptmobile.data.search

import java.util.concurrent.atomic.AtomicInteger

class CitationNumberer {
    private val next = AtomicInteger(1)

    fun take(): Int = next.getAndIncrement()
}
