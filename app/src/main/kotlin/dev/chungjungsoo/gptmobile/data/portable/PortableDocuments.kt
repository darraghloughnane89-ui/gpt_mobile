package dev.chungjungsoo.gptmobile.data.portable

import dev.chungjungsoo.gptmobile.data.bot.BotAvatarPalette
import dev.chungjungsoo.gptmobile.data.bot.BotDraft
import dev.chungjungsoo.gptmobile.data.bot.StarterPrompts
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

/**
 * Plain JSON exchange for bots, skills, and saved prompts.
 * Files are pretty-printed and unsigned. Deep links use the compact form.
 * Secret, token, memory, and identity keys are dropped before any field is read.
 */
object PortableDocuments {
    private val compact = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val pretty = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    fun exportBot(draft: BotDraft): String {
        val clean = draft.sanitized()
        val tools = buildList {
            if (clean.currentDate) add(BuiltInAgentTool.CURRENT_DATE)
            if (clean.readUrl) add(BuiltInAgentTool.READ_URL)
            if (!clean.webSearchConnectionUid.isNullOrBlank()) add(PortablePolicy.WEB_SEARCH)
            if (clean.mcpConnectionUids.isNotEmpty()) add(PortablePolicy.MCP)
        }
        val body = PortableBotBody(
            name = clean.name,
            avatarEmoji = clean.avatarEmoji.takeIf { it.isNotEmpty() },
            avatarColor = clean.avatarColor,
            instructions = clean.instructions,
            defaultModel = clean.defaultModel.takeIf { it.isNotEmpty() },
            starterPrompts = clean.starterPrompts,
            tools = tools
        )
        return encode(PortableKind.BOT, botItem(body), pretty = true)
    }

    fun exportSkill(body: PortableSkillBody): String = encode(PortableKind.SKILL, skillItem(body), pretty = true)

    fun exportPrompt(body: PortablePromptBody): String = encode(PortableKind.PROMPT, promptItem(body), pretty = true)

    fun shareLink(preview: PortablePreview): String? {
        val json = when (preview) {
            is PortablePreview.Bot -> encode(PortableKind.BOT, botItem(preview.body), pretty = false)
            is PortablePreview.Skill -> encode(PortableKind.SKILL, skillItem(preview.body), pretty = false)
            is PortablePreview.Prompt -> encode(PortableKind.PROMPT, promptItem(preview.body), pretty = false)
        }
        val payload = PortablePayload.compress(json)
        if (payload.length > PortableLimits.MAX_LINK_PAYLOAD_CHARS) return null
        return "${PortableLimits.SCHEME}://${PortableLimits.HOST_IMPORT}?${PortableLimits.QUERY}=$payload"
    }

    fun looksLikeDocument(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val head = text.take(4096).trimStart().removePrefix("\uFEFF")
        return head.startsWith("{") && head.contains("\"${PortableLimits.FORMAT}\"")
    }

    fun declaredKind(fileName: String?, mime: String?): PortableKind? =
        PortableKind.fromFileName(fileName) ?: PortableKind.fromMime(mime)

    fun consider(text: String?, fileName: String? = null, mime: String? = null): PortableResult? {
        val declared = declaredKind(fileName, mime)
        val body = text?.removePrefix("\uFEFF").orEmpty()
        if (declared == null && !looksLikeDocument(body)) return null
        if (body.toByteArray(Charsets.UTF_8).size > PortableLimits.MAX_DOCUMENT_BYTES) {
            return PortableResult.Failure(PortableFailure.TOO_LARGE)
        }
        return parse(body, declared)
    }

    fun parse(text: String, declared: PortableKind? = null): PortableResult {
        if (text.toByteArray(Charsets.UTF_8).size > PortableLimits.MAX_DOCUMENT_BYTES) {
            return PortableResult.Failure(PortableFailure.TOO_LARGE)
        }
        val root = try {
            compact.parseToJsonElement(text.removePrefix("\uFEFF")) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return PortableResult.Failure(PortableFailure.NOT_A_DOCUMENT)
        val warnings = linkedSetOf<PortableWarning>()
        if (root.containsKey("signature")) warnings += PortableWarning.IGNORED_SIGNATURE
        val format = root.string("format")
        if (format != PortableLimits.FORMAT) return PortableResult.Failure(PortableFailure.NOT_A_DOCUMENT)
        val version = root["version"]?.jsonPrimitive?.intOrNull
        if (version != PortableLimits.VERSION) return PortableResult.Failure(PortableFailure.UNSUPPORTED_VERSION)
        val kind = PortableKind.fromWire(root.string("kind"))
            ?: return PortableResult.Failure(PortableFailure.NOT_A_DOCUMENT)
        if (declared != null && declared != kind) return PortableResult.Failure(PortableFailure.KIND_MISMATCH)
        val item = root["item"] as? JsonObject ?: return PortableResult.Failure(PortableFailure.INVALID_ITEM)
        val cleaned = strip(item, warnings)
        return when (kind) {
            PortableKind.BOT -> botPreview(cleaned, warnings)
            PortableKind.SKILL -> skillPreview(cleaned, warnings)
            PortableKind.PROMPT -> promptPreview(cleaned, warnings)
        }
    }

    fun decodeLink(scheme: String?, host: String?, payload: String?): PortableResult {
        if (!scheme.equals(PortableLimits.SCHEME, ignoreCase = true)) {
            return PortableResult.Failure(PortableFailure.NOT_A_DOCUMENT)
        }
        val expected = when (host?.lowercase()) {
            PortableLimits.HOST_IMPORT -> null
            else -> PortableKind.fromWire(host?.lowercase())
        }
        if (host.isNullOrBlank() || (expected == null && !host.equals(PortableLimits.HOST_IMPORT, ignoreCase = true))) {
            return PortableResult.Failure(PortableFailure.NOT_A_DOCUMENT)
        }
        if (payload.isNullOrBlank()) return PortableResult.Failure(PortableFailure.NOT_A_DOCUMENT)
        if (payload.length > PortableLimits.MAX_LINK_PAYLOAD_CHARS) {
            return PortableResult.Failure(PortableFailure.LINK_TOO_LARGE)
        }
        return when (val decoded = PortablePayload.decompress(payload)) {
            PortablePayload.Decoded.TooLarge -> PortableResult.Failure(PortableFailure.TOO_LARGE)
            PortablePayload.Decoded.Invalid -> PortableResult.Failure(PortableFailure.NOT_A_DOCUMENT)
            is PortablePayload.Decoded.Bytes -> parse(decoded.value.toString(Charsets.UTF_8), expected)
        }
    }

    private fun encode(kind: PortableKind, item: JsonObject, pretty: Boolean): String {
        val root = buildJsonObject {
            put("format", JsonPrimitive(PortableLimits.FORMAT))
            put("version", JsonPrimitive(PortableLimits.VERSION))
            put("kind", JsonPrimitive(kind.wire))
            put("item", item)
        }
        val printer = if (pretty) this.pretty else compact
        return printer.encodeToString(serializer<JsonElement>(), root)
    }

    private fun botItem(body: PortableBotBody): JsonObject = buildJsonObject {
        put("name", JsonPrimitive(body.name))
        body.avatarEmoji?.let { put("avatarEmoji", JsonPrimitive(it)) }
        put("avatarColor", JsonPrimitive(body.avatarColor))
        put("instructions", JsonPrimitive(body.instructions))
        body.defaultModel?.let { put("defaultModel", JsonPrimitive(it)) }
        put("starterPrompts", jsonStrings(body.starterPrompts))
        put("tools", jsonStrings(body.tools))
    }

    private fun skillItem(body: PortableSkillBody): JsonObject = buildJsonObject {
        put("name", JsonPrimitive(body.name))
        put("title", JsonPrimitive(body.title))
        put("description", JsonPrimitive(body.description))
        put("instructions", JsonPrimitive(body.instructions))
        put("tools", jsonStrings(body.tools))
        put("examples", jsonStrings(body.examples))
    }

    private fun promptItem(body: PortablePromptBody): JsonObject = buildJsonObject {
        put("title", JsonPrimitive(body.title))
        put("text", JsonPrimitive(body.text))
    }

    private fun botPreview(item: JsonObject, warnings: MutableSet<PortableWarning>): PortableResult {
        val name = untrustedText(item.string("name").orEmpty(), PortableLimits.MAX_NAME)
        if (name.isBlank()) return PortableResult.Failure(PortableFailure.INVALID_ITEM)
        val tools = PortablePolicy.cleanTools(item.strings("tools"))
        val grants = PortablePolicy.botGrants(tools)
        val body = PortableBotBody(
            name = name,
            avatarEmoji = untrustedText(item.string("avatarEmoji").orEmpty(), BotDraft.MAX_EMOJI_LENGTH)
                .takeIf { it.isNotEmpty() },
            avatarColor = BotAvatarPalette.sanitize(item.string("avatarColor")),
            instructions = untrustedText(item.string("instructions").orEmpty(), PortableLimits.MAX_INSTRUCTIONS),
            defaultModel = untrustedText(item.string("defaultModel").orEmpty(), BotDraft.MAX_MODEL_LENGTH)
                .takeIf { it.isNotEmpty() },
            starterPrompts = StarterPrompts.decode(
                StarterPrompts.encode(
                    item.strings("starterPrompts").map {
                        untrustedText(it, StarterPrompts.MAX_LENGTH)
                    }
                )
            ),
            tools = tools
        )
        return PortableResult.Ok(PortablePreview.Bot(body, grants.requests, warnings.toSet()))
    }

    private fun skillPreview(item: JsonObject, warnings: MutableSet<PortableWarning>): PortableResult {
        val requestedName = item.string("name").orEmpty().ifBlank { item.string("title").orEmpty() }
        val slug = portableSlug(requestedName)
            ?: return PortableResult.Failure(PortableFailure.INVALID_ITEM)
        if (slug != requestedName.trim()) warnings += PortableWarning.SLUG_ADJUSTED
        val description = untrustedText(item.string("description").orEmpty(), PortableLimits.MAX_DESCRIPTION)
        val instructions = untrustedText(item.string("instructions").orEmpty(), PortableLimits.MAX_INSTRUCTIONS)
        if (description.isBlank() || instructions.isBlank()) {
            return PortableResult.Failure(PortableFailure.INVALID_ITEM)
        }
        val tools = PortablePolicy.cleanTools(item.strings("tools"))
        val body = PortableSkillBody(
            name = slug,
            title = untrustedText(item.string("title").orEmpty(), PortableLimits.MAX_NAME).ifBlank { slug },
            description = description,
            instructions = instructions,
            tools = tools,
            examples = item.strings("examples")
                .map { untrustedText(it, PortableLimits.MAX_EXAMPLE) }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(PortableLimits.MAX_EXAMPLES)
        )
        return PortableResult.Ok(
            PortablePreview.Skill(body, PortablePolicy.skillRequests(tools), warnings.toSet())
        )
    }

    private fun promptPreview(item: JsonObject, warnings: MutableSet<PortableWarning>): PortableResult {
        val text = untrustedText(item.string("text").orEmpty(), PortableLimits.MAX_INSTRUCTIONS)
        if (text.isBlank()) return PortableResult.Failure(PortableFailure.INVALID_ITEM)
        val fallback = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val title = untrustedText(item.string("title").orEmpty(), PortableLimits.MAX_NAME)
            .ifBlank { untrustedText(fallback, PortableLimits.MAX_NAME) }
            .ifBlank { "Prompt" }
        val tools = PortablePolicy.withheld(item.strings("tools"))
        return PortableResult.Ok(
            PortablePreview.Prompt(PortablePromptBody(title, text), tools, warnings.toSet())
        )
    }

    private fun strip(item: JsonObject, warnings: MutableSet<PortableWarning>): JsonObject {
        var strippedSecrets = false
        var strippedIdentity = false
        fun walk(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> buildJsonObject {
                element.forEach { (key, value) ->
                    when (keyClass(key)) {
                        KeyClass.SECRET -> strippedSecrets = true
                        KeyClass.IDENTITY -> strippedIdentity = true
                        KeyClass.KEEP -> put(key, walk(value))
                    }
                }
            }
            is JsonArray -> JsonArray(element.map(::walk))
            else -> element
        }
        val cleaned = walk(item) as JsonObject
        if (strippedSecrets) warnings += PortableWarning.STRIPPED_SECRETS
        if (strippedIdentity) warnings += PortableWarning.IGNORED_IDENTITY
        return cleaned
    }

    private fun jsonStrings(values: List<String>): JsonArray = buildJsonArray {
        values.forEach { add(JsonPrimitive(it)) }
    }
}

private enum class KeyClass { SECRET, IDENTITY, KEEP }

private fun keyClass(key: String): KeyClass {
    val normalized = key.lowercase().replace("_", "").replace("-", "")
    return when (normalized) {
        in SECRET_KEYS -> KeyClass.SECRET
        in IDENTITY_KEYS -> KeyClass.IDENTITY
        else -> KeyClass.KEEP
    }
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.strings(key: String): List<String> {
    val value = this[key] ?: return emptyList()
    return when (value) {
        is JsonArray -> value.mapNotNull { element ->
            (element as? JsonPrimitive)?.contentOrNull
        }
        is JsonPrimitive -> listOfNotNull(value.contentOrNull)
        else -> emptyList()
    }
}

private val SECRET_KEYS = setOf(
    "token",
    "tokens",
    "apikey",
    "secret",
    "secrets",
    "password",
    "passwd",
    "authorization",
    "bearer",
    "credential",
    "credentials",
    "memory",
    "memories",
    "memoryfacts",
    "refreshtoken",
    "accesstoken",
    "connectionuid",
    "endpoint",
    "endpointurl",
    "vault",
    "oauth",
    "privatekey",
    "cookie",
    "cookies",
    "sessiontoken",
    "idtoken",
    "clientsecret",
    "signature"
)

private val IDENTITY_KEYS = setOf(
    "id",
    "uid",
    "botuid",
    "skillid",
    "promptid",
    "defaultplatformuid",
    "platformuid",
    "bindinguid",
    "createdat",
    "updatedat",
    "usecount",
    "thumbsup",
    "thumbsdown",
    "pinned",
    "origin",
    "builtin",
    "lastusedat"
)
