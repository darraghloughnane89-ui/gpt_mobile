package dev.chungjungsoo.gptmobile.data.bot

object BotAvatarPalette {
    const val DEFAULT = "#6750A4"
    val colors = listOf(
        "#6750A4",
        "#7D5260",
        "#006A6A",
        "#8C4A00",
        "#3F691C",
        "#0058BE"
    )

    private val colorPattern = Regex("#[0-9A-F]{6}")

    fun sanitize(hex: String?): String {
        val normalized = hex?.trim()?.uppercase().orEmpty()
        return if (colorPattern.matches(normalized)) normalized else DEFAULT
    }
}
