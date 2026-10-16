package dev.chungjungsoo.gptmobile.data.backup

import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevision
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.memory.Memory
import dev.chungjungsoo.gptmobile.data.security.SecretRedactor
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.GeminiSafetySettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ChatArchive(
    val version: Int = 1,
    val exportedAtEpochSeconds: Long,
    val platforms: List<ArchivePlatform> = emptyList(),
    val chats: List<ArchiveChat> = emptyList(),
    val memories: List<ArchiveMemory> = emptyList()
)

@Serializable
data class ArchivePlatform(
    val uid: String,
    val name: String,
    val compatibleType: String,
    val enabled: Boolean = false,
    val apiUrl: String,
    val model: String,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxTokens: Int? = null,
    val accelerator: String? = null,
    val systemPrompt: String? = null,
    val stream: Boolean = true,
    val reasoning: Boolean = false,
    val timeout: Int = 30,
    val harassmentSafetyThreshold: String = GeminiSafetySettings.BLOCK_NONE,
    val hateSpeechSafetyThreshold: String = GeminiSafetySettings.BLOCK_NONE,
    val sexuallyExplicitSafetyThreshold: String = GeminiSafetySettings.BLOCK_NONE,
    val dangerousContentSafetyThreshold: String = GeminiSafetySettings.BLOCK_NONE,
    val resumableReplies: Boolean = false
)

@Serializable
data class ArchiveChat(
    val title: String,
    val enabledPlatform: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val memoryEnabled: Boolean = true,
    val messages: List<ArchiveMessage> = emptyList()
)

@Serializable
data class ArchiveMessage(
    val id: Int,
    val content: String,
    val thoughts: String = "",
    val platformType: String? = null,
    val createdAt: Long,
    val linkedMessageId: Int = 0,
    val revisions: List<AssistantRevision> = emptyList(),
    val activeRevisionIndex: Int = -1,
    val timeline: List<AssistantTimelineItem> = emptyList()
)

@Serializable
data class ArchiveMemory(
    val text: String,
    val source: String,
    val paused: Boolean = false,
    val projectId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

data class ArchiveImportCounts(
    val platformsAdded: Int,
    val chatsAdded: Int,
    val memoriesWritten: Int
)

object ChatArchiveCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(archive: ChatArchive): String = json.encodeToString(redact(archive))

    fun redact(archive: ChatArchive): ChatArchive = archive.copy(
        platforms = archive.platforms.map { platform ->
            platform.copy(systemPrompt = platform.systemPrompt?.let(SecretRedactor::redact))
        },
        chats = archive.chats.map { chat ->
            chat.copy(messages = chat.messages.map(::redactMessage))
        },
        memories = archive.memories.map { memory -> memory.copy(text = SecretRedactor.redact(memory.text)) }
    )

    private fun redactMessage(message: ArchiveMessage): ArchiveMessage = message.copy(
        content = SecretRedactor.redact(message.content),
        thoughts = SecretRedactor.redact(message.thoughts),
        revisions = message.revisions.map { revision ->
            revision.copy(
                content = SecretRedactor.redact(revision.content),
                thoughts = SecretRedactor.redact(revision.thoughts),
                timeline = revision.timeline.map { item -> item.copy(content = SecretRedactor.redact(item.content)) }
            )
        },
        timeline = message.timeline.map { item -> item.copy(content = SecretRedactor.redact(item.content)) }
    )

    fun decode(text: String): ChatArchive = json.decodeFromString(text)

    fun platform(platform: PlatformV2): ArchivePlatform = ArchivePlatform(
        uid = platform.uid,
        name = platform.name,
        compatibleType = platform.compatibleType.name,
        enabled = platform.enabled,
        apiUrl = platform.apiUrl,
        model = platform.model,
        temperature = platform.temperature,
        topP = platform.topP,
        topK = platform.topK,
        maxTokens = platform.maxTokens,
        accelerator = platform.accelerator,
        systemPrompt = platform.systemPrompt,
        stream = platform.stream,
        reasoning = platform.reasoning,
        timeout = platform.timeout,
        harassmentSafetyThreshold = platform.harassmentSafetyThreshold,
        hateSpeechSafetyThreshold = platform.hateSpeechSafetyThreshold,
        sexuallyExplicitSafetyThreshold = platform.sexuallyExplicitSafetyThreshold,
        dangerousContentSafetyThreshold = platform.dangerousContentSafetyThreshold,
        resumableReplies = platform.resumableReplies
    )

    fun message(message: MessageV2): ArchiveMessage = ArchiveMessage(
        id = message.id,
        content = message.content,
        thoughts = message.thoughts,
        platformType = message.platformType,
        createdAt = message.createdAt,
        linkedMessageId = message.linkedMessageId,
        revisions = message.revisions,
        activeRevisionIndex = message.activeRevisionIndex,
        timeline = message.timeline
    )

    fun memory(memory: Memory): ArchiveMemory = ArchiveMemory(
        text = memory.text,
        source = memory.source,
        paused = memory.paused,
        projectId = memory.projectId,
        createdAt = memory.createdAt,
        updatedAt = memory.updatedAt
    )

    fun platformsToInsert(existingUids: Set<String>, incoming: List<ArchivePlatform>): List<PlatformV2> {
        return incoming.mapNotNull { platform ->
            if (platform.uid in existingUids) return@mapNotNull null
            val type = try {
                ClientType.valueOf(platform.compatibleType)
            } catch (_: IllegalArgumentException) {
                null
            } ?: return@mapNotNull null
            PlatformV2(
                uid = platform.uid,
                name = platform.name,
                compatibleType = type,
                enabled = platform.enabled,
                apiUrl = platform.apiUrl,
                token = null,
                secretRef = null,
                model = platform.model,
                temperature = platform.temperature,
                topP = platform.topP,
                topK = platform.topK,
                maxTokens = platform.maxTokens,
                accelerator = platform.accelerator,
                systemPrompt = platform.systemPrompt,
                stream = platform.stream,
                reasoning = platform.reasoning,
                timeout = platform.timeout,
                harassmentSafetyThreshold = platform.harassmentSafetyThreshold,
                hateSpeechSafetyThreshold = platform.hateSpeechSafetyThreshold,
                sexuallyExplicitSafetyThreshold = platform.sexuallyExplicitSafetyThreshold,
                dangerousContentSafetyThreshold = platform.dangerousContentSafetyThreshold,
                resumableReplies = platform.resumableReplies
            )
        }
    }
}
