package dev.chungjungsoo.gptmobile.data.project.archive

import kotlinx.serialization.Serializable

@Serializable
data class ProjectImportMap(
    val chats: Map<String, Int> = emptyMap(),
    val files: Map<String, Int> = emptyMap()
)
