package dev.chungjungsoo.gptmobile.data.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    @Test
    fun shouldLock_disabled_staysOpen() {
        assertFalse(AppLockPolicy.shouldLock(false, 0, 0L, 10_000L))
    }

    @Test
    fun shouldLock_immediateTimeout_locksOnReturn() {
        assertTrue(AppLockPolicy.shouldLock(true, 0, 1_000L, 1_001L))
    }

    @Test
    fun shouldLock_withinTimeout_staysOpen() {
        assertFalse(AppLockPolicy.shouldLock(true, 60, 0L, 59_000L))
    }

    @Test
    fun shouldLock_afterTimeout_locks() {
        assertTrue(AppLockPolicy.shouldLock(true, 60, 0L, 60_000L))
    }
}
