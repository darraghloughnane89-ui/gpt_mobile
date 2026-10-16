package dev.chungjungsoo.gptmobile.data.project

object ProjectContextLimits {
    const val MAX_NAME_CHARS = 80
    const val MAX_INSTRUCTION_CHARS = 8_000
    const val MAX_FILE_CHARS = 6_000
    const val MAX_CONTEXT_CHARS = 24_000
    const val MAX_STORED_BYTES = 8 * 1024 * 1024
}
