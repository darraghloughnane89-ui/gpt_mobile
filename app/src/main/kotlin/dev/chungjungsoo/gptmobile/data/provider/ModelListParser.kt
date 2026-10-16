package dev.chungjungsoo.gptmobile.data.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ModelListParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String): List<RemoteModel> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val rows = arrayField(root, "data") + arrayField(root, "models")
        val models = LinkedHashMap<String, RemoteModel>()
        rows.forEach { element ->
            val row = element as? JsonObject ?: return@forEach
            val rawId = text(row, "id") ?: text(row, "name") ?: return@forEach
            val id = rawId.removePrefix("models/").trim()
            if (id.isEmpty() || id == "latest") return@forEach
            models.putIfAbsent(id, RemoteModel(id = id, ownedBy = text(row, "owned_by"), supportsVision = supportsVision(row, id)))
            aliases(row).forEach { alias ->
                val normalized = alias.trim()
                if (normalized.isNotEmpty() && normalized != "latest") {
                    models.putIfAbsent(
                        normalized,
                        RemoteModel(
                            id = normalized,
                            ownedBy = text(row, "owned_by"),
                            supportsVision = supportsVision(row, normalized)
                        )
                    )
                }
            }
        }
        return models.values.toList()
    }

    private fun arrayField(root: JsonObject, name: String): List<kotlinx.serialization.json.JsonElement> {
        val value = root[name] as? JsonArray ?: return emptyList()
        return value
    }

    private fun aliases(row: JsonObject): List<String> {
        val value = row["aliases"] as? JsonArray ?: return emptyList()
        return value.mapNotNull { element ->
            runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
        }
    }

    private fun supportsVision(row: JsonObject, id: String): Boolean {
        val modalities = row["input_modalities"] as? JsonArray
        val listed = modalities?.any { element ->
            runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()?.equals("image", ignoreCase = true) == true
        } == true
        return listed || id.contains("vision", ignoreCase = true)
    }

    private fun text(row: JsonObject, name: String): String? = runCatching { row[name]?.jsonPrimitive?.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }
}
