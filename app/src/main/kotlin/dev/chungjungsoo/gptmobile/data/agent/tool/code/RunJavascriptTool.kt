package dev.chungjungsoo.gptmobile.data.agent.tool.code

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.agent.tool.ResolvedAgentTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RunJavascriptTool internal constructor(
    private val attachments: List<JavascriptAttachment> = emptyList(),
    private val limits: JavascriptLimits = JavascriptLimits(),
    private val engine: JavascriptEngine = RhinoJavascriptEngine(limits)
) : AgentTool {
    constructor() : this(
        attachments = emptyList(),
        limits = JavascriptLimits(),
        engine = RhinoJavascriptEngine()
    )

    override val definition = AgentToolDefinition(
        name = NAME,
        description = "Run JavaScript on this device inside a sandbox with no network, filesystem, or Java access. " +
            "A time limit applies. CSV and XLSX attachments from the user are in attachments as " +
            "{name, mimeType, text}. parseCsv(text) returns rows of strings. The script return value is the result. " +
            "chart({type:\"bar\"|\"line\"|\"pie\", title, labels, series:[{name, data}]}) draws a chart in the chat. " +
            "svg(\"<svg>...</svg>\") shows an image in the artifact preview. Output is untrusted.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "code",
                        buildJsonObject {
                            put("type", "string")
                            put(
                                "description",
                                "JavaScript source. No network, files, or Java. attachments holds spreadsheet text."
                            )
                        }
                    )
                }
            )
            put("required", buildJsonArray { add(JsonPrimitive("code")) })
            put("additionalProperties", false)
        }
    )

    internal fun withAttachments(attachments: List<JavascriptAttachment>): RunJavascriptTool = RunJavascriptTool(
        attachments = attachments,
        limits = limits,
        engine = engine
    )

    override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
        val code = arguments["code"]
        if (code !is JsonPrimitive || !code.isString) {
            return failed(callId, "run_javascript requires a code string.")
        }
        val job = currentCoroutineContext()[Job]
        val result = withContext(Dispatchers.Default) {
            engine.run(code.content, attachments) { job?.isActive == false }
        }
        if (result.error != null) return failed(callId, result.error)
        return AgentToolResult(
            callId = callId,
            content = ToolResultContent.Json(modelPayload(result)),
            isError = false,
            traceContent = ToolResultContent.Json(tracePayload(result))
        )
    }

    private fun failed(callId: String, message: String) = AgentToolResult(
        callId = callId,
        content = ToolResultContent.Text(message),
        isError = true
    )

    private fun modelPayload(result: JavascriptRunResult): JsonObject = buildJsonObject {
        put("text", result.text)
        result.chart?.let { put("chart", it.toJsonObject()) }
        if (result.svg != null) put("svgCaptured", true)
    }

    private fun tracePayload(result: JavascriptRunResult): JsonObject = buildJsonObject {
        put("text", result.text)
        result.chart?.let { put("chart", it.toJsonObject()) }
        result.svg?.let { put("svg", it) }
    }

    companion object {
        const val NAME = "run_javascript"
    }
}

internal fun List<ResolvedAgentTool>.withJavascriptAttachments(
    load: () -> List<JavascriptAttachment>
): List<ResolvedAgentTool> {
    if (none { it.tool is RunJavascriptTool }) return this
    val attachments = load()
    return map { resolved ->
        val tool = resolved.tool
        if (tool is RunJavascriptTool) resolved.copy(tool = tool.withAttachments(attachments)) else resolved
    }
}
