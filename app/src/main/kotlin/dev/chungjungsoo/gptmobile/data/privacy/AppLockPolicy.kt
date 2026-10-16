package dev.chungjungsoo.gptmobile.data.privacy

object AppLockPolicy {
    val timeoutChoicesSeconds: List<Int> = listOf(0, 60, 300, 900)

    fun shouldLock(
        enabled: Boolean,
        timeoutSeconds: Int,
        backgroundedAtMillis: Long,
        nowMillis: Long
    ): Boolean {
        if (!enabled) return false
        if (timeoutSeconds <= 0) return true
        return nowMillis - backgroundedAtMillis >= timeoutSeconds * 1000L
    }
}
