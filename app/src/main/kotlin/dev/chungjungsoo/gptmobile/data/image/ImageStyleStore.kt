package dev.chungjungsoo.gptmobile.data.image

import java.io.File

class ImageStyleStore(
    private val directory: File
) {
    fun load(chatId: Int): ImageStylePreference {
        val file = File(directory, fileName(chatId))
        if (!file.isFile) return ImageStylePreference()
        val values = file.readLines().mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
        }.toMap()
        return ImageStylePreference(
            enabled = values["enabled"] == "true",
            style = values["style"]?.takeIf { it.isNotBlank() }
        )
    }

    fun save(chatId: Int, preference: ImageStylePreference) {
        directory.mkdirs()
        val style = preference.style?.replace(Regex("[\\r\\n=]"), " ")?.trim().orEmpty()
        File(directory, fileName(chatId)).writeText("enabled=${preference.enabled}\nstyle=$style\n")
    }

    private fun fileName(chatId: Int): String = "chat-$chatId.txt"
}
