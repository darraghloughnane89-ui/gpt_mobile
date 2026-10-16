package dev.chungjungsoo.gptmobile.util

private const val STORAGE_RESERVE_BYTES = 32L * 1024L * 1024L

internal fun isLowStorageFailure(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        val name = current.javaClass.name
        if (name.endsWith("SQLiteFullException") || name.endsWith("SQLiteDiskIOException")) return true
        val message = current.message?.lowercase().orEmpty()
        if (
            "enospc" in message ||
            "no space left" in message ||
            "sqlite_full" in message ||
            "database or disk is full" in message ||
            "disk full" in message
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

internal fun persistenceFailureMessage(error: Throwable, lowStorageMessage: String): String = if (isLowStorageFailure(error)) {
    lowStorageMessage
} else {
    error.message?.takeIf { it.isNotBlank() } ?: "Failed to save this turn."
}

/** True when [neededBytes] fit with a reserve left for the database and crash log. */
internal fun hasRoomForBytes(
    availableBytes: Long,
    neededBytes: Long,
    reserveBytes: Long = STORAGE_RESERVE_BYTES
): Boolean = neededBytes >= 0 && availableBytes - reserveBytes >= neededBytes
