package dev.chungjungsoo.gptmobile.data.agent.tool.code

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

enum class ChartType {
    BAR,
    LINE,
    PIE
}

data class ChartSeries(
    val name: String,
    val data: List<Double>
)

/**
 * A small chart description produced by sandboxed JavaScript.
 * Only finite numbers and short labels survive parsing, so the Compose renderer
 * never trusts raw tool text.
 */
data class ChartSpec(
    val type: ChartType,
    val title: String,
    val labels: List<String>,
    val series: List<ChartSeries>
) {
    fun toJsonObject(): JsonObject = buildJsonObject {
        put("type", type.name.lowercase())
        if (title.isNotEmpty()) put("title", title)
        put("labels", buildJsonArray { labels.forEach { add(JsonPrimitive(it)) } })
        put(
            "series",
            buildJsonArray {
                series.forEach { item ->
                    add(
                        buildJsonObject {
                            put("name", item.name)
                            put("data", buildJsonArray { item.data.forEach { value -> add(jsonNumber(value)) } })
                        }
                    )
                }
            }
        )
    }

    companion object {
        const val MAX_SERIES = 8
        const val MAX_POINTS = 32
        private const val MAX_TITLE = 80
        private const val MAX_LABEL = 24
        private const val MAX_ABS_VALUE = 1.0e12

        fun parse(element: JsonElement): ChartSpec? {
            val obj = element as? JsonObject ?: return null
            val type = when (obj.string("type")) {
                "bar" -> ChartType.BAR
                "line" -> ChartType.LINE
                "pie" -> ChartType.PIE
                else -> return null
            }
            val seriesElement = obj["series"] as? JsonArray ?: return null
            if (seriesElement.isEmpty() || seriesElement.size > MAX_SERIES) return null
            val series = seriesElement.mapIndexed { index, item ->
                val seriesObject = item as? JsonObject ?: return null
                val data = seriesObject["data"] as? JsonArray ?: return null
                if (data.isEmpty() || data.size > MAX_POINTS) return null
                val numbers = data.map { value ->
                    val number = (value as? JsonPrimitive)?.doubleOrNull ?: return null
                    if (!number.isFinite() || number > MAX_ABS_VALUE || number < -MAX_ABS_VALUE) return null
                    number
                }
                ChartSeries(
                    name = seriesObject.string("name")?.take(MAX_LABEL)?.ifBlank { null } ?: "Series ${index + 1}",
                    data = numbers
                )
            }
            val length = series.first().data.size
            if (series.any { it.data.size != length }) return null
            val provided = (obj["labels"] as? JsonArray)?.map { label ->
                (label as? JsonPrimitive)?.content?.take(MAX_LABEL).orEmpty()
            }
            val labels = when {
                provided != null && provided.size == length -> provided
                provided == null -> List(length) { index -> (index + 1).toString() }
                else -> return null
            }
            return ChartSpec(
                type = type,
                title = obj.string("title")?.take(MAX_TITLE).orEmpty(),
                labels = labels,
                series = series
            )
        }

        private fun JsonObject.string(key: String): String? {
            val value = this[key] as? JsonPrimitive ?: return null
            return if (value.isString) value.content else null
        }

        private fun jsonNumber(value: Double): JsonPrimitive {
            val asLong = value.toLong()
            return if (value == asLong.toDouble()) JsonPrimitive(asLong) else JsonPrimitive(value)
        }
    }
}
