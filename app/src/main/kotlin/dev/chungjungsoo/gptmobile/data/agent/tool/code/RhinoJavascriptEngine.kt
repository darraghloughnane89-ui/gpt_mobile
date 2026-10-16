package dev.chungjungsoo.gptmobile.data.agent.tool.code

import java.util.concurrent.CancellationException
import kotlin.coroutines.cancellation.CancellationException as CoroutineCancellation
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.UniqueTag

/**
 * Mozilla Rhino 1.7.15 (MPL-2.0) runs the script in interpreter mode.
 * QuickJS Android bindings were not used: they ship native code for every ABI
 * and do not expose an instruction or memory interrupt, so a coroutine timeout
 * cannot stop a running script. Rhino is one pure-Java jar, initSafeStandardObjects
 * omits Java live-connect, and the class shutter rejects any Java class name.
 */
internal data class JavascriptLimits(
    val timeoutMillis: Long = 1_500,
    val maxInstructions: Long = 300_000,
    val maxSourceChars: Int = 32_000,
    val maxResultChars: Int = 8_000,
    val maxStackDepth: Int = 48,
    val checkInterval: Int = 2_000
)

internal data class JavascriptRunResult(
    val text: String,
    val chart: ChartSpec? = null,
    val svg: String? = null,
    val error: String? = null
)

internal interface JavascriptEngine {
    fun run(
        code: String,
        attachments: List<JavascriptAttachment>,
        cancelled: () -> Boolean = { false }
    ): JavascriptRunResult
}

internal class RhinoJavascriptEngine(
    private val limits: JavascriptLimits = JavascriptLimits()
) : JavascriptEngine {
    private val contexts = object : ContextFactory() {
        override fun makeContext(): Context {
            val context = super.makeContext()
            context.optimizationLevel = -1
            context.languageVersion = Context.VERSION_ES6
            context.maximumInterpreterStackDepth = limits.maxStackDepth
            context.setClassShutter(ClassShutter { false })
            context.instructionObserverThreshold = limits.checkInterval
            return context
        }

        override fun observeInstructionCount(cx: Context, instructionCount: Int) {
            val state = cx.getThreadLocal(STATE_KEY) as? SandboxState ?: return
            if (state.cancelled()) throw CoroutineCancellation("JavaScript was canceled.")
            state.instructions += instructionCount.toLong()
            if (state.instructions > state.maxInstructions || System.nanoTime() > state.deadlineNanos) {
                throw SandboxLimitException("JavaScript exceeded its time limit.")
            }
        }
    }

    override fun run(
        code: String,
        attachments: List<JavascriptAttachment>,
        cancelled: () -> Boolean
    ): JavascriptRunResult {
        val source = code.trim()
        if (source.isEmpty()) return failure("JavaScript source is empty.")
        if (source.length > limits.maxSourceChars) return failure("JavaScript source is too long.")
        return try {
            contexts.call { cx ->
                execute(cx, source, attachments.take(MAX_JAVASCRIPT_ATTACHMENTS), cancelled)
            } as JavascriptRunResult
        } catch (error: Throwable) {
            when {
                cancelled() || error.isCancellation() -> throw CoroutineCancellation("JavaScript was canceled.", error)

                error.find<SandboxLimitException>() != null -> failure("JavaScript exceeded its time limit.")

                error.find<OutOfMemoryError>() != null -> failure("JavaScript exceeded the memory limit.")

                error.find<StackOverflowError>() != null -> failure("JavaScript exceeded the stack limit.")

                error.find<RhinoException>() != null -> failure(cleanMessage(error))

                else -> failure("JavaScript failed.")
            }
        }
    }

    private fun execute(
        cx: Context,
        source: String,
        attachments: List<JavascriptAttachment>,
        cancelled: () -> Boolean
    ): JavascriptRunResult {
        val state = SandboxState(
            maxInstructions = PRELUDE_INSTRUCTIONS,
            deadlineNanos = System.nanoTime() + PRELUDE_TIMEOUT_NANOS,
            cancelled = cancelled
        )
        cx.putThreadLocal(STATE_KEY, state)
        val scope = cx.initSafeStandardObjects()
        var chart: ChartSpec? = null
        var svg: String? = null
        installAttachments(cx, scope, attachments)
        ScriptableObject.putProperty(
            scope,
            "parseCsv",
            hostFunction { _, arguments ->
                val text = stringArg(arguments, "parseCsv")
                val rows = try {
                    SpreadsheetText.parseCsv(text)
                } catch (error: IllegalArgumentException) {
                    throw EvaluatorException(error.message)
                }
                rowsToArray(cx, scope, rows)
            }
        )
        ScriptableObject.putProperty(
            scope,
            "chart",
            hostFunction { _, arguments ->
                val spec = ChartSpec.parse(toJson(arguments.firstOrNull())) ?: throw EvaluatorException(
                    "chart() needs type bar, line, or pie, labels, and numeric series."
                )
                chart = spec
                Undefined.instance
            }
        )
        ScriptableObject.putProperty(
            scope,
            "svg",
            hostFunction { _, arguments ->
                val markup = SvgArtifact.sanitize(stringArg(arguments, "svg")) ?: throw EvaluatorException(
                    "svg() markup was rejected."
                )
                svg = markup
                Undefined.instance
            }
        )
        cx.evaluateString(scope, REPEAT_CAP, "sandbox.js", 1, null)
        state.instructions = 0
        state.maxInstructions = limits.maxInstructions
        state.deadlineNanos = System.nanoTime() + limits.timeoutMillis * 1_000_000
        val result = cx.evaluateString(scope, source, "user.js", 1, null)
        return finish(result, chart, svg)
    }

    private fun finish(result: Any?, chartCall: ChartSpec?, svgCall: String?): JavascriptRunResult {
        val envelope = envelopeOf(result)
        val notes = mutableListOf<String>()
        var chart = chartCall
        var svg = svgCall
        if (chart == null && envelope != null) {
            val raw = property(envelope, "chart")
            if (raw != null) {
                chart = ChartSpec.parse(toJson(raw))
                if (chart == null) notes += "Chart spec was rejected."
            }
        }
        if (svg == null && envelope != null) {
            val raw = property(envelope, "svg")
            if (raw != null) {
                svg = SvgArtifact.sanitize(Context.toString(raw))
                if (svg == null) notes += "SVG markup was rejected."
            }
        }
        val base = when {
            envelope != null && property(envelope, "text") != null -> textOf(property(envelope, "text"))

            envelope != null -> ""

            else -> textOf(result)
        }
        val text = listOf(base, notes.joinToString(" "))
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .ifBlank { if (chart == null && svg == null) "(no result)" else "" }
            .take(limits.maxResultChars)
        return JavascriptRunResult(text = text, chart = chart, svg = svg)
    }

    private fun installAttachments(cx: Context, scope: Scriptable, attachments: List<JavascriptAttachment>) {
        var remaining = MAX_ATTACHMENT_CHARS
        val included = attachments.mapNotNull { attachment ->
            if (remaining <= 0) return@mapNotNull null
            val text = attachment.text.take(remaining.coerceAtMost(MAX_ATTACHMENT_CHARS))
            remaining -= text.length
            attachment.copy(
                name = attachment.name.take(120),
                mimeType = attachment.mimeType.take(120),
                text = text
            )
        }
        val array = cx.newArray(scope, included.size)
        included.forEachIndexed { index, attachment ->
            val item = cx.newObject(scope)
            item.put("name", item, attachment.name)
            item.put("mimeType", item, attachment.mimeType)
            item.put("text", item, attachment.text)
            array.put(index, array, item)
        }
        ScriptableObject.putProperty(scope, "attachments", array)
    }

    private fun rowsToArray(cx: Context, scope: Scriptable, rows: List<List<String>>): Scriptable {
        val array = cx.newArray(scope, rows.size)
        rows.forEachIndexed { rowIndex, row ->
            val nested = cx.newArray(scope, row.size)
            row.forEachIndexed { column, cell -> nested.put(column, nested, cell) }
            array.put(rowIndex, array, nested)
        }
        return array
    }

    private fun textOf(value: Any?): String {
        if (value == null || Undefined.isUndefined(value) || value === UniqueTag.NOT_FOUND) return ""
        if (value is Number || value is Boolean || (value is CharSequence && value !is Scriptable)) {
            return primitiveText(value).take(limits.maxResultChars)
        }
        return toJson(value).toString().take(limits.maxResultChars)
    }

    private fun primitiveText(value: Any): String = when (value) {
        is Boolean -> value.toString()

        is Number -> {
            val number = value.toDouble()
            if (!number.isFinite()) {
                ""
            } else {
                val asLong = number.toLong()
                if (number == asLong.toDouble()) asLong.toString() else number.toString()
            }
        }

        else -> Context.toString(value)
    }

    private fun envelopeOf(value: Any?): Scriptable? {
        if (value !is Scriptable || value is NativeArray || value is Function) return null
        val names = value.ids.map { it.toString() }.toSet()
        return if ("text" in names || "chart" in names || "svg" in names) value else null
    }

    private fun property(value: Scriptable, name: String): Any? {
        val found = value.get(name, value)
        if (found == null || found === UniqueTag.NOT_FOUND || Undefined.isUndefined(found)) return null
        return found
    }

    private fun toJson(value: Any?): JsonElement = toJson(value, mutableSetOf(), Budget(), 0)

    private fun toJson(value: Any?, seen: MutableSet<Int>, budget: Budget, depth: Int): JsonElement {
        if (value == null || Undefined.isUndefined(value) || value === UniqueTag.NOT_FOUND) return JsonNull
        if (value is String) return JsonPrimitive(value.take(MAX_JSON_STRING))
        if (value is CharSequence && value !is Scriptable) return JsonPrimitive(value.toString().take(MAX_JSON_STRING))
        if (value is Boolean) return JsonPrimitive(value)
        if (value is Number) {
            val number = value.toDouble()
            if (!number.isFinite()) return JsonNull
            val asLong = number.toLong()
            return if (number == asLong.toDouble()) JsonPrimitive(asLong) else JsonPrimitive(number)
        }
        if (value is Function || value !is Scriptable) return JsonPrimitive(Context.toString(value).take(MAX_JSON_STRING))
        if (depth >= MAX_DEPTH || budget.nodes >= MAX_NODES) return JsonPrimitive("…")
        val identity = System.identityHashCode(value)
        if (!seen.add(identity)) return JsonPrimitive("…")
        budget.nodes += 1
        return try {
            if (value is NativeArray) {
                val length = value.getLength().coerceAtMost(MAX_ARRAY.toLong())
                buildJsonArray {
                    for (index in 0 until length) add(toJson(value.get(index.toInt(), value), seen, budget, depth + 1))
                }
            } else {
                buildJsonObject {
                    var count = 0
                    for (id in value.ids) {
                        if (count >= MAX_FIELDS) break
                        val key = id.toString().take(80)
                        val child = if (id is Int) value.get(id, value) else value.get(key, value)
                        put(key, toJson(child, seen, budget, depth + 1))
                        count += 1
                    }
                }
            }
        } finally {
            seen.remove(identity)
        }
    }

    private fun hostFunction(block: (Context, Array<out Any?>) -> Any?): BaseFunction = object : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>?): Any = block(cx, args ?: emptyArray()) ?: Undefined.instance
    }

    private fun stringArg(args: Array<out Any?>, name: String): String {
        val value = args.firstOrNull()
        if (value == null || Undefined.isUndefined(value)) throw EvaluatorException("$name requires a string.")
        return Context.toString(value)
    }

    private fun failure(message: String) = JavascriptRunResult(text = "", error = message.take(400))

    private fun cleanMessage(error: Throwable): String {
        val rhino = error.find<RhinoException>()
        val raw = rhino?.message?.lineSequence()?.firstOrNull()?.take(400)
        return raw?.takeIf { it.isNotBlank() } ?: "JavaScript failed."
    }

    private inline fun <reified T : Throwable> Throwable.find(): T? =
        generateSequence(this) { it.cause }.filterIsInstance<T>().firstOrNull()

    private fun Throwable.isCancellation(): Boolean =
        generateSequence(this) { it.cause }.any { it is CancellationException || it is CoroutineCancellation }

    private class SandboxState(
        var maxInstructions: Long,
        var deadlineNanos: Long,
        val cancelled: () -> Boolean
    ) {
        var instructions: Long = 0
    }

    private class Budget {
        var nodes: Int = 0
    }

    private class SandboxLimitException(message: String) : RuntimeException(message)

    private companion object {
        val STATE_KEY = Any()
        const val PRELUDE_INSTRUCTIONS = 50_000L
        const val PRELUDE_TIMEOUT_NANOS = 2_000_000_000L
        const val MAX_ATTACHMENT_CHARS = 64_000
        const val MAX_JSON_STRING = 2_000
        const val MAX_DEPTH = 8
        const val MAX_NODES = 200
        const val MAX_ARRAY = 100
        const val MAX_FIELDS = 32
        val REPEAT_CAP = """
            (function () {
              var repeat = String.prototype.repeat;
              String.prototype.repeat = function (count) {
                var n = Number(count);
                if (!isFinite(n) || n < 0 || n > 10000 || String(this).length * n > 100000) {
                  throw new Error("string is too large");
                }
                return repeat.call(this, n);
              };
            })();
        """.trimIndent()
    }
}
