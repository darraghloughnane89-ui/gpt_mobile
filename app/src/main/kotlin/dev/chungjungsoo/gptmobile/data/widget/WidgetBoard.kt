package dev.chungjungsoo.gptmobile.data.widget

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RoutineResult(
    val routineId: String,
    val name: String,
    val summary: String,
    val chatId: Int,
    val enabledPlatforms: List<String>,
    val completedAtEpochSeconds: Long
)

data class WidgetBoard(
    val pinnedChatIds: List<Int> = emptyList(),
    val showContentWhenLocked: Boolean = false,
    val latestRoutine: RoutineResult? = null
)

object WidgetBoardCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodePinned(ids: List<Int>): String = ids.distinct().joinToString(",")

    fun decodePinned(raw: String?): List<Int> = raw.orEmpty()
        .split(',')
        .mapNotNull { token -> token.trim().toIntOrNull() }
        .distinct()

    fun encodeRoutine(result: RoutineResult): String = json.encodeToString(RoutineResult.serializer(), result)

    fun decodeRoutine(raw: String?): RoutineResult? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(RoutineResult.serializer(), raw) }.getOrNull()
    }
}
