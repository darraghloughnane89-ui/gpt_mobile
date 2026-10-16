package dev.chungjungsoo.gptmobile.data.provider

import kotlinx.serialization.Serializable

@Serializable
data class RemoteModel(
    val id: String,
    val ownedBy: String? = null,
    val supportsVision: Boolean = false
)
