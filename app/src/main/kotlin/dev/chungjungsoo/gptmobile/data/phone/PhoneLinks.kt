package dev.chungjungsoo.gptmobile.data.phone

import dev.chungjungsoo.gptmobile.presentation.common.Route
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Deep links owned by phone integration.
 *
 * ```
 * dev.chungjungsoo.gptmobile://phone/new-chat
 * dev.chungjungsoo.gptmobile://phone/ask?input=text
 * dev.chungjungsoo.gptmobile://phone/ask?input=mic
 * dev.chungjungsoo.gptmobile://phone/camera
 * dev.chungjungsoo.gptmobile://phone/voice
 * dev.chungjungsoo.gptmobile://phone/chat/{chatId}?platforms={comma-separated-uids}
 * ```
 *
 * [VOICE_NAV_ROUTE] is the Compose route the voice stream should register.
 * This stream does not add that destination.
 */
object PhoneLinks {
    const val SCHEME = "dev.chungjungsoo.gptmobile"
    const val HOST = "phone"
    const val PATH_NEW_CHAT = "/new-chat"
    const val PATH_ASK = "/ask"
    const val PATH_CAMERA = "/camera"
    const val PATH_VOICE = "/voice"
    const val PATH_CHAT = "/chat"
    const val QUERY_PLATFORMS = "platforms"
    const val QUERY_INPUT = "input"
    const val INPUT_TEXT = "text"
    const val INPUT_MIC = "mic"
    const val VOICE_NAV_ROUTE = Route.VOICE_SESSION

    const val NEW_CHAT = "$SCHEME://$HOST/new-chat"
    const val CAMERA = "$SCHEME://$HOST/camera"
    const val VOICE = "$SCHEME://$HOST/voice"

    fun ask(input: String): String = "$SCHEME://$HOST/ask?$QUERY_INPUT=$input"

    fun chat(chatId: Int, enabledPlatforms: List<String>): String {
        val encoded = URLEncoder.encode(enabledPlatforms.joinToString(","), Charsets.UTF_8.name())
        return "$SCHEME://$HOST/chat/$chatId?$QUERY_PLATFORMS=$encoded"
    }

    fun isPhoneDeepLink(scheme: String?, host: String?): Boolean = scheme == SCHEME && host == HOST

    data class Parts(
        val scheme: String?,
        val host: String?,
        val path: String?,
        val chatId: Int? = null,
        val platforms: List<String> = emptyList(),
        val input: String? = null
    )

    fun parts(scheme: String?, host: String?, path: String?, rawQuery: String?): Parts {
        val query = parseQuery(rawQuery)
        val segments = path.orEmpty().split('/').filter { it.isNotBlank() }
        val chatId = if (segments.firstOrNull() == "chat") segments.getOrNull(1)?.toIntOrNull() else null
        val platforms = query[QUERY_PLATFORMS]
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val input = query[QUERY_INPUT]?.takeIf { it == INPUT_TEXT || it == INPUT_MIC }
        return Parts(
            scheme = scheme,
            host = host,
            path = path,
            chatId = chatId,
            platforms = platforms,
            input = input
        )
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&').mapNotNull { pair ->
            if (pair.isBlank()) return@mapNotNull null
            val key = URLDecoder.decode(pair.substringBefore('='), Charsets.UTF_8.name())
            val value = URLDecoder.decode(pair.substringAfter('=', ""), Charsets.UTF_8.name())
            key to value
        }.toMap()
    }
}
