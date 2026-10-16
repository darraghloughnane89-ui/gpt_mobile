package dev.chungjungsoo.gptmobile.data.image

data class ImageStylePreference(
    val enabled: Boolean = false,
    val style: String? = null
)

sealed class ImageStyleCommand {
    data object Toggle : ImageStyleCommand()
    data object On : ImageStyleCommand()
    data object Off : ImageStyleCommand()
    data class Set(val style: String) : ImageStyleCommand()
}

object ImageStyleCommands {
    private val command = Regex("^/image-style(?:\\s+(.*))?$", RegexOption.IGNORE_CASE)

    fun parse(text: String): ImageStyleCommand? {
        val match = command.matchEntire(text.trim()) ?: return null
        val argument = match.groupValues[1].trim()
        return when {
            argument.isEmpty() -> ImageStyleCommand.Toggle
            argument.equals("off", ignoreCase = true) ||
                argument.equals("none", ignoreCase = true) ||
                argument.equals("clear", ignoreCase = true) -> ImageStyleCommand.Off

            argument.equals("on", ignoreCase = true) -> ImageStyleCommand.On
            else -> ImageStyleCommand.Set(argument.replace(Regex("[\\r\\n]+"), " ").trim())
        }
    }

    fun apply(command: ImageStyleCommand, current: ImageStylePreference): ImageStylePreference = when (command) {
        ImageStyleCommand.Toggle -> current.copy(enabled = !current.enabled)
        ImageStyleCommand.On -> current.copy(enabled = true)
        ImageStyleCommand.Off -> ImageStylePreference(enabled = false, style = null)
        is ImageStyleCommand.Set -> ImageStylePreference(enabled = true, style = command.style)
    }

    fun styledPrompt(prompt: String, preference: ImageStylePreference): String {
        val trimmed = prompt.trim()
        if (!preference.enabled) return trimmed
        val style = preference.style?.trim().orEmpty()
        if (style.isEmpty()) return trimmed
        return "$trimmed\n\nStyle: $style"
    }
}
