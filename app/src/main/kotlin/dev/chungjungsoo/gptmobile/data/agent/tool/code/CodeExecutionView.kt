package dev.chungjungsoo.gptmobile.data.agent.tool.code

import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal data class CodeExecutionView(
    val code: String,
    val text: String,
    val chart: ChartSpec?,
    val svg: String?,
    val svgRejected: Boolean
)

private val viewJson = Json { ignoreUnknownKeys = true }

internal fun ToolEvent.isJavascriptTool(): Boolean = toolName == BuiltInAgentTool.RUN_JAVASCRIPT ||
    modelToolName == BuiltInAgentTool.RUN_JAVASCRIPT

internal fun ToolEvent.toCodeExecutionView(): CodeExecutionView = codeExecutionView(
    arguments = arguments,
    result = result,
    isError = isError
)

internal fun codeExecutionView(arguments: String, result: String?, isError: Boolean): CodeExecutionView {
    val parsed = result?.let { runCatching { viewJson.parseToJsonElement(it) }.getOrNull() as? JsonObject }
    val rawSvg = parsed?.get("svg")?.let { (it as? JsonPrimitive)?.contentOrNull }
    val svg = rawSvg?.let(SvgArtifact::sanitize)
    return CodeExecutionView(
        code = codeFromArguments(arguments),
        text = when {
            parsed != null -> parsed.string("text") ?: if (isError) result.orEmpty() else ""
            else -> result.orEmpty()
        },
        chart = parsed?.get("chart")?.let(ChartSpec::parse),
        svg = svg,
        svgRejected = !rawSvg.isNullOrBlank() && svg == null && rawSvg.contains("<svg", ignoreCase = true)
    )
}

internal fun codeFromArguments(arguments: String): String {
    val parsed = runCatching { viewJson.parseToJsonElement(arguments) }.getOrNull() as? JsonObject
    return parsed?.string("code") ?: arguments
}

private fun JsonObject.string(key: String): String? {
    val value = this[key] as? JsonPrimitive ?: return null
    return if (value.isString) value.content else null
}
