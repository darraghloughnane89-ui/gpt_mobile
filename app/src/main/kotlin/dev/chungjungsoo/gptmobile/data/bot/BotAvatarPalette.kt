package dev.chungjungsoo.gptmobile.data.bot

object BotAvatarPalette {
    const val DEFAULT = "#B42318"
    val colors = listOf(
        "#B42318",
        "#8A4F00",
        "#6E3B2E",
        "#C2410C",
        "#7A3E12",
        "#3F2A24"
    )

    private val colorPattern = Regex("#[0-9A-F]{6}")

    fun sanitize(hex: String?): String {
        val normalized = hex?.trim()?.uppercase().orEmpty()
        return if (colorPattern.matches(normalized)) normalized else DEFAULT
    }
}
