package dev.chungjungsoo.gptmobile.data.diagnostics

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

data class StoredCrash(
    val fileName: String,
    val timeMs: Long,
    val summary: String
)

data class StoredProviderError(
    val timeMs: Long,
    val kind: ProviderFailureKind,
    val platform: String,
    val model: String,
    val detail: String
)

class DiagnosticsStore(
    private val root: File
) {
    private val lock = Any()

    fun writeCrash(
        threadName: String,
        throwable: Throwable,
        timeMs: Long = System.currentTimeMillis()
    ): File = synchronized(lock) {
        val report = DiagnosticRedactor.redact(rawCrashReport(threadName, throwable, timeMs))
        val directory = File(root, CRASH_DIR).apply { mkdirs() }
        var file = File(directory, "crash-$timeMs.txt")
        var suffix = 2
        while (file.exists()) {
            file = File(directory, "crash-$timeMs-$suffix.txt")
            suffix += 1
        }
        file.writeText(report)
        trimCrashFiles(directory)
        file
    }

    fun listCrashes(): List<StoredCrash> = synchronized(lock) { readCrashes() }

    fun appendProviderError(entry: StoredProviderError) {
        synchronized(lock) {
            val file = providerErrorFile().apply { parentFile?.mkdirs() }
            file.appendText(encodeProviderError(entry) + "\n")
            val lines = file.readLines().filter { it.isNotBlank() }
            if (lines.size > MAX_PROVIDER_ERRORS) {
                file.writeText(lines.takeLast(MAX_PROVIDER_ERRORS).joinToString("\n") + "\n")
            }
        }
    }

    fun listProviderErrors(): List<StoredProviderError> = synchronized(lock) {
        readProviderErrors().asReversed()
    }

    fun buildShareReport(): String = synchronized(lock) {
        val crashes = readCrashBodies()
        val errors = readProviderErrors()
        val raw = buildString {
            appendLine("Robin diagnostics report")
            appendLine("Stored only on this device. This file is created when you choose to share it.")
            appendLine()
            appendLine("## Crashes")
            if (crashes.isEmpty()) {
                appendLine("(none)")
            } else {
                crashes.forEach { (name, body) ->
                    appendLine("--- $name")
                    appendLine(body)
                    appendLine()
                }
            }
            appendLine("## Provider errors")
            if (errors.isEmpty()) {
                appendLine("(none)")
            } else {
                errors.asReversed().forEach { error ->
                    appendLine("${error.timeMs} ${error.kind.name} ${error.platform} ${error.model}")
                    appendLine(error.detail)
                    appendLine()
                }
            }
        }
        DiagnosticRedactor.redact(raw)
    }

    private fun readCrashes(): List<StoredCrash> {
        val directory = File(root, CRASH_DIR)
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles { file -> file.isFile && file.name.endsWith(".txt") }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val timeMs = crashTimeMs(file)
                val summary = file.readLines()
                    .filter { line -> line.startsWith("exception:") || line.startsWith("message:") }
                    .joinToString("\n")
                    .ifBlank { file.name }
                StoredCrash(file.name, timeMs, summary)
            }
    }

    private fun readCrashBodies(): List<Pair<String, String>> {
        val directory = File(root, CRASH_DIR)
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles { file -> file.isFile && file.name.endsWith(".txt") }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { file -> file.name to file.readText() }
    }

    private fun readProviderErrors(): List<StoredProviderError> {
        val file = providerErrorFile()
        if (!file.isFile) return emptyList()
        return file.readLines().mapNotNull(::decodeProviderError)
    }

    private fun providerErrorFile(): File = File(root, PROVIDER_ERROR_LOG)

    private fun trimCrashFiles(directory: File) {
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(".txt") }
            .orEmpty()
            .sortedBy { it.lastModified() }
        val extra = files.size - MAX_CRASHES
        if (extra > 0) {
            files.take(extra).forEach { it.delete() }
        }
    }

    private fun crashTimeMs(file: File): Long {
        val stem = file.name.removePrefix("crash-").removeSuffix(".txt")
        return stem.substringBefore("-").toLongOrNull() ?: file.lastModified()
    }

    companion object {
        const val CRASH_DIR = "crashes"
        const val PROVIDER_ERROR_LOG = "provider-errors.log"
        const val MAX_CRASHES = 30
        const val MAX_PROVIDER_ERRORS = 200
    }
}

internal fun recordProviderFailure(
    store: DiagnosticsStore,
    platformName: String,
    model: String,
    message: String,
    timeMs: Long = System.currentTimeMillis()
) {
    if (message.isBlank()) return
    runCatching {
        val kind = ProviderFailureClassifier.classify(message) ?: ProviderFailureKind.GENERIC
        store.appendProviderError(
            StoredProviderError(
                timeMs = timeMs,
                kind = kind,
                platform = DiagnosticRedactor.redact(platformName).take(120),
                model = DiagnosticRedactor.redact(model).take(120),
                detail = DiagnosticRedactor.redact(message).take(500)
            )
        )
    }
}

internal fun encodeProviderError(entry: StoredProviderError): String = listOf(
    entry.timeMs.toString(),
    entry.kind.name,
    encodeField(entry.platform),
    encodeField(entry.model),
    encodeField(entry.detail)
).joinToString("\t")

internal fun decodeProviderError(line: String): StoredProviderError? {
    val parts = line.split('\t')
    if (parts.size != 5) return null
    val timeMs = parts[0].toLongOrNull() ?: return null
    val kind = runCatching { ProviderFailureKind.valueOf(parts[1]) }.getOrNull() ?: return null
    return StoredProviderError(
        timeMs = timeMs,
        kind = kind,
        platform = decodeField(parts[2]),
        model = decodeField(parts[3]),
        detail = decodeField(parts[4])
    )
}

private fun encodeField(value: String): String = buildString {
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
}

private fun decodeField(value: String): String = buildString {
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '\\' && index + 1 < value.length) {
            when (value[index + 1]) {
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                '\\' -> append('\\')
                else -> append(character)
            }
            index += if (value[index + 1] == 'n' || value[index + 1] == 'r' || value[index + 1] == 't' || value[index + 1] == '\\') 2 else 1
        } else {
            append(character)
            index += 1
        }
    }
}

internal fun rawCrashReport(threadName: String, throwable: Throwable, timeMs: Long): String {
    val stack = StringWriter()
    throwable.printStackTrace(PrintWriter(stack))
    return buildString {
        appendLine("Robin local crash report")
        appendLine("time_ms: $timeMs")
        appendLine("thread: $threadName")
        appendLine("exception: ${throwable.javaClass.name}")
        appendLine("message: ${throwable.message.orEmpty()}")
        appendLine("stacktrace:")
        append(stack.toString())
    }
}
