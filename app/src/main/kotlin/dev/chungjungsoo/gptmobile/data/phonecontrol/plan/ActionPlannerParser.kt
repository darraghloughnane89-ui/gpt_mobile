/*
 * XML tool-call shape adapted from mobilerun fast-agent prompts.
 * Copyright (c) 2025 Niels Schmidt, MIT License.
 * See NOTICE.
 */
package dev.chungjungsoo.gptmobile.data.phonecontrol.plan

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ActionPlannerParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val untrustedBlock = Regex("(?is)<untrusted\\b[^>]*>.*?</untrusted>")
    private val invokeBlock = Regex("(?is)<invoke\\s+name=\"([^\"]+)\">(.*?)</invoke>")
    private val parameter = Regex("(?is)<parameter\\s+name=\"([^\"]+)\">(.*?)</parameter>")

    fun parseModelOutput(text: String): List<PlannedAction> {
        val modelText = untrustedBlock.replace(text, "")
        return parseXml(modelText) + parseJsonObjects(modelText)
    }

    fun fromToolCall(name: String, arguments: JsonObject): PlannedAction? = actionFrom(name, arguments.toMap(), ActionSource.MODEL_TOOL)

    private fun parseXml(text: String): List<PlannedAction> = invokeBlock.findAll(text).mapNotNull { match ->
        val name = match.groupValues[1]
        val body = match.groupValues[2]
        val values = parameter.findAll(body).associate { it.groupValues[1] to it.groupValues[2].trim() }
        actionFrom(name, values.mapValues { JsonPrimitive(it.value) }, ActionSource.MODEL_TOOL)
    }.toList()

    private fun parseJsonObjects(text: String): List<PlannedAction> {
        val results = mutableListOf<PlannedAction>()
        var searchFrom = 0
        while (searchFrom < text.length) {
            val start = text.indexOf('{', searchFrom)
            if (start < 0) break
            val end = matchingBrace(text, start)
            if (end == null) break
            val slice = text.substring(start, end + 1)
            val parsed = runCatching { json.parseToJsonElement(slice).jsonObject }.getOrNull()
            if (parsed != null && (parsed.containsKey("name") || parsed.containsKey("action"))) {
                val name = parsed.string("name") ?: parsed.string("action") ?: ""
                val arguments = parsed["arguments"]?.jsonObject ?: parsed
                actionFrom(name, arguments.toMap(), ActionSource.MODEL_TOOL)?.let(results::add)
            }
            searchFrom = end + 1
        }
        return results
    }

    private fun actionFrom(name: String, arguments: Map<String, kotlinx.serialization.json.JsonElement>, source: ActionSource): PlannedAction? {
        val normalized = name.trim().lowercase()
        val index = arguments.int("index")
        val text = arguments.string("text")
        val packageName = arguments.string("package") ?: arguments.string("packageName") ?: arguments.string("id")
        return when (normalized) {
            "click", "tap", "long_press" -> PlannedAction(
                kind = PhoneActionKind.TAP,
                index = index,
                x = arguments.int("x"),
                y = arguments.int("y"),
                targetText = arguments.string("target_text") ?: text,
                resourceId = arguments.string("resource_id") ?: arguments.string("resourceId"),
                source = source
            )
            "type", "type_text", "focus_and_input_text" -> PlannedAction(
                kind = PhoneActionKind.TYPE,
                index = index,
                text = text,
                source = source
            )
            "swipe" -> PlannedAction(
                kind = PhoneActionKind.SWIPE,
                x = arguments.int("x") ?: arguments.int("start_x"),
                y = arguments.int("y") ?: arguments.int("start_y"),
                x2 = arguments.int("x2") ?: arguments.int("end_x"),
                y2 = arguments.int("y2") ?: arguments.int("end_y"),
                source = source
            )
            "scroll" -> PlannedAction(kind = PhoneActionKind.SCROLL, text = text, source = source)
            "system_button" -> when (arguments.string("button")?.lowercase()) {
                "home" -> PlannedAction(kind = PhoneActionKind.HOME, source = source)
                "back" -> PlannedAction(kind = PhoneActionKind.BACK, source = source)
                else -> null
            }
            "back" -> PlannedAction(kind = PhoneActionKind.BACK, source = source)
            "home" -> PlannedAction(kind = PhoneActionKind.HOME, source = source)
            "open_app", "launch_app" -> PlannedAction(
                kind = PhoneActionKind.LAUNCH_APP,
                text = text,
                packageName = packageName,
                source = source
            )
            "open_intent" -> PlannedAction(kind = PhoneActionKind.OPEN_INTENT, text = text, packageName = packageName, source = source)
            "app_function" -> PlannedAction(kind = PhoneActionKind.APP_FUNCTION, text = text, packageName = packageName, source = source)
            "request_autofill", "type_secret" -> PlannedAction(kind = PhoneActionKind.REQUEST_AUTOFILL, source = source)
            "reply_notification" -> PlannedAction(kind = PhoneActionKind.REPLY_NOTIFICATION, text = text, source = source)
            "wait" -> PlannedAction(kind = PhoneActionKind.WAIT, source = source)
            "complete" -> PlannedAction(
                kind = PhoneActionKind.COMPLETE,
                success = arguments.bool("success"),
                message = arguments.string("message") ?: arguments.string("reason"),
                source = source
            )
            else -> null
        }
    }

    private fun JsonObject.toMap(): Map<String, kotlinx.serialization.json.JsonElement> = this

    private fun Map<String, kotlinx.serialization.json.JsonElement>.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun Map<String, kotlinx.serialization.json.JsonElement>.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull ?: this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    private fun Map<String, kotlinx.serialization.json.JsonElement>.bool(key: String): Boolean? {
        val primitive = this[key]?.jsonPrimitive ?: return null
        return primitive.booleanOrNull ?: primitive.contentOrNull?.toBooleanStrictOrNull()
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun matchingBrace(text: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val character = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
                continue
            }
            when (character) {
                '"' -> inString = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        return null
    }
}
