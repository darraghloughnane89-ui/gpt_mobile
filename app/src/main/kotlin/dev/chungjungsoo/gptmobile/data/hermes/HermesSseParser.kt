package dev.chungjungsoo.gptmobile.data.hermes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class HermesSseParser {
    private var eventName: String? = null
    private val data = StringBuilder()

    fun consume(line: String): List<HermesStreamEvent> {
        if (line.isEmpty()) {
            val name = eventName
            val payload = data.toString()
            eventName = null
            data.clear()
            if (name == null && payload.isEmpty()) return emptyList()
            return decode(name, payload)
        }
        if (line.startsWith(":")) return emptyList()
        if (line.startsWith("event:")) {
            eventName = line.substringAfter("event:").trim()
            return emptyList()
        }
        if (line.startsWith("data:")) {
            if (data.isNotEmpty()) data.append('\n')
            data.append(line.removePrefix("data:").trim())
        }
        return emptyList()
    }

    fun finish(): List<HermesStreamEvent> = consume("")

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parseJsonBody(body: String): List<HermesStreamEvent> {
            val events = decode(null, body.trim())
            val terminal = events.any { it is HermesStreamEvent.Completed || it is HermesStreamEvent.Failed }
            return if (terminal) events else events + HermesStreamEvent.Completed(null)
        }

        private fun decode(eventName: String?, payload: String): List<HermesStreamEvent> {
            if (payload == "[DONE]") return listOf(HermesStreamEvent.Completed(null))
            val element = try {
                json.parseToJsonElement(payload)
            } catch (_: Exception) {
                return emptyList()
            }
            val obj = element as? JsonObject ?: return emptyList()
            val name = eventName.orEmpty()
            if (name == "hermes.tool.progress" || name == "tool.started" || name == "tool.completed") {
                return listOf(HermesStreamEvent.Tool(toolLabel(obj)))
            }
            if (name == "message.interim") {
                val alreadyStreamed = (obj["already_streamed"] as? JsonPrimitive)?.contentOrNull == "true"
                if (alreadyStreamed) return emptyList()
                val text = stringField(obj, "text").orEmpty()
                return if (text.isEmpty()) emptyList() else listOf(HermesStreamEvent.Text(text))
            }
            if (name == "message.delta" || name == "assistant.delta") {
                val text = stringField(obj, "text") ?: textOf(obj["delta"]) ?: textOf(obj["content"]).orEmpty()
                return if (text.isEmpty()) emptyList() else listOf(HermesStreamEvent.Text(text))
            }
            if (name == "run.completed") {
                return listOf(HermesStreamEvent.Completed(stringField(obj, "output") ?: stringField(obj, "text")))
            }
            if (name == "run.failed" || name == "run.cancelled" || name == "run.interrupted") {
                val message = stringField(obj, "error") ?: stringField(obj, "message") ?: name
                return listOf(HermesStreamEvent.Failed(message))
            }
            errorObject(obj)?.let { return listOf(HermesStreamEvent.Failed(it)) }
            return chatChoice(obj)
        }

        private fun chatChoice(obj: JsonObject): List<HermesStreamEvent> {
            val choice = (obj["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return emptyList()
            val delta = choice["delta"] as? JsonObject
            val message = choice["message"] as? JsonObject
            val content = textOf(delta?.get("content")) ?: textOf(message?.get("content"))
            val finish = (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull
            val events = mutableListOf<HermesStreamEvent>()
            if (!content.isNullOrEmpty()) events += HermesStreamEvent.Text(content)
            if (finish == "error" || finish == "cancelled") {
                events += HermesStreamEvent.Failed(
                    stringField(choice, "error") ?: "Hermes finished with $finish."
                )
            }
            return events
        }

        private fun errorObject(obj: JsonObject): String? {
            val error = obj["error"] ?: return null
            val nested = error as? JsonObject
            return nested?.let { stringField(it, "message") } ?: (error as? JsonPrimitive)?.contentOrNull
        }

        private fun toolLabel(obj: JsonObject): String {
            val tool = stringField(obj, "tool") ?: stringField(obj, "name") ?: "tool"
            val preview = stringField(obj, "preview").orEmpty()
            return if (preview.isBlank()) tool else "$tool: $preview"
        }

        private fun stringField(obj: JsonObject, name: String): String? = (obj[name] as? JsonPrimitive)?.contentOrNull

        private fun textOf(element: JsonElement?): String? = when (element) {
            is JsonPrimitive -> element.contentOrNull
            is JsonArray -> element.mapNotNull { part ->
                val obj = part as? JsonObject ?: return@mapNotNull null
                stringField(obj, "text")
            }.joinToString("").ifBlank { null }
            else -> null
        }
    }
}
