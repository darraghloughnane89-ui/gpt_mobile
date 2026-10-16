package dev.chungjungsoo.gptmobile.data.gguf

import kotlinx.serialization.Serializable

@Serializable
data class GgufModelRecord(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val contextSize: Int = GgufRamPolicy.DEFAULT_CONTEXT,
    val temperature: Float = 0.8f,
    val sourceLabel: String? = null,
    val createdAtEpochMs: Long = 0L
)
