package dev.chungjungsoo.gptmobile.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageFailureTest {
    @Test
    fun sqliteAndEnospcFailuresAreLowStorage() {
        assertTrue(isLowStorageFailure(SQLiteFullException("database or disk is full")))
        assertTrue(isLowStorageFailure(RuntimeException("write failed: ENOSPC")))
        assertTrue(isLowStorageFailure(IllegalStateException("wrapper", SQLiteDiskIOException("io"))))
        assertFalse(isLowStorageFailure(IllegalStateException("constraint failed")))
    }

    @Test
    fun persistenceMessageUsesTheLowStorageCopy() {
        val lowStorage = "Not enough storage to save this. Free some space and retry."
        assertEquals(lowStorage, persistenceFailureMessage(SQLiteFullException("full"), lowStorage))
        assertEquals("constraint failed", persistenceFailureMessage(IllegalStateException("constraint failed"), lowStorage))
        assertEquals("Failed to save this turn.", persistenceFailureMessage(IllegalStateException("  "), lowStorage))
    }

    @Test
    fun roomCheckKeepsAReserve() {
        val reserve = 32L * 1024L * 1024L
        assertTrue(hasRoomForBytes(availableBytes = reserve + 100, neededBytes = 100))
        assertFalse(hasRoomForBytes(availableBytes = reserve + 99, neededBytes = 100))
        assertFalse(hasRoomForBytes(availableBytes = 1_000, neededBytes = -1))
    }

    private class SQLiteFullException(message: String) : RuntimeException(message)
    private class SQLiteDiskIOException(message: String) : RuntimeException(message)
}
