package dev.chungjungsoo.gptmobile.data.security

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultSession @Inject constructor() {
    @Volatile
    private var unlockedUntilMillis: Long = 0L
    private var clock: () -> Long = { SystemClock.elapsedRealtime() }

    fun isUnlocked(nowMillis: Long = clock()): Boolean = nowMillis < unlockedUntilMillis

    fun unlock(nowMillis: Long = clock()) {
        unlockedUntilMillis = nowMillis + TIMEOUT_MILLIS
    }

    internal fun useClock(clock: () -> Long) {
        this.clock = clock
    }

    fun lock() {
        unlockedUntilMillis = 0L
    }

    companion object {
        const val TIMEOUT_SECONDS = VaultKeyPolicy.AUTH_VALIDITY_SECONDS
        const val TIMEOUT_MILLIS = TIMEOUT_SECONDS * 1000L
    }
}

class VaultLockedException :
    SecretVaultException("Unlock the key vault before using a saved key.")
