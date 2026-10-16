package dev.chungjungsoo.gptmobile.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OffMainLazyTest {
    @Test
    fun initializerRunsOnCallerWhenAlreadyOffMain() {
        val caller = Thread.currentThread()
        val lazyValue = OffMainLazy(name = "room", isMainThread = { false }) {
            assertEquals(caller, Thread.currentThread())
            "ready"
        }

        assertEquals("ready", lazyValue.get())
    }

    @Test
    fun initializerLeavesTheMainThread() {
        val caller = Thread.currentThread()
        val lazyValue = OffMainLazy(
            name = "ktor-client",
            isMainThread = { Thread.currentThread() == caller }
        ) {
            assertNotEquals(caller, Thread.currentThread())
            "client"
        }

        assertEquals("client", lazyValue.get())
    }

    @Test
    fun initializerRunsOnce() {
        val calls = AtomicInteger()
        val lazyValue = OffMainLazy(name = "litert-lm", isMainThread = { false }) {
            calls.incrementAndGet()
            Any()
        }

        val first = lazyValue.get()
        val second = lazyValue.get()

        assertEquals(1, calls.get())
        assertSame(first, second)
    }

    @Test
    fun concurrentMainReadersShareOneInstance() {
        val caller = Thread.currentThread()
        val calls = AtomicInteger()
        val started = CountDownLatch(2)
        val lazyValue = OffMainLazy(
            name = "room-chat-v2",
            isMainThread = { Thread.currentThread() == caller }
        ) {
            calls.incrementAndGet()
            Thread.sleep(50)
            Any()
        }
        val first = AtomicReference<Any>()
        val second = AtomicReference<Any>()
        val readers = listOf(
            Thread {
                started.countDown()
                first.set(lazyValue.get())
            },
            Thread {
                started.countDown()
                second.set(lazyValue.get())
            }
        )
        readers.forEach { it.start() }
        readers.forEach { it.join() }

        assertEquals(1, calls.get())
        assertSame(first.get(), second.get())
    }
}
