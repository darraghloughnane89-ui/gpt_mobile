package dev.chungjungsoo.gptmobile.data.fetch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Public YouTube captions without the paid Data API.
 *
 * Verified 2026-10-16 against `jNQXAC9IVRw`: POST the ANDROID InnerTube player
 * (playability OK, caption tracks present), then GET the track `baseUrl` as `fmt=json3`.
 * WEB timedtext URLs are PoToken-gated and return an empty body, so this client does not use them.
 * [PUBLIC_INNERTUBE_KEY] is the key embedded in YouTube's web client, not a Data API credential.
 */
object YouTubeTranscript {
    const val PUBLIC_INNERTUBE_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val CLIENT_VERSION = "20.10.38"
    private const val USER_AGENT = "com.google.android.youtube/$CLIENT_VERSION (Linux; U; Android 14) gzip"

    private val json = Json { ignoreUnknownKeys = true }

    data class CaptionTrack(
        val baseUrl: String,
        val languageCode: String,
        val generated: Boolean
    )

    data class Exchange(
        val url: String,
        val method: String = "GET",
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null
    )

    fun playerRequest(videoId: String): Exchange {
        val body = """
            {"context":{"client":{"clientName":"ANDROID","clientVersion":"$CLIENT_VERSION","hl":"en","gl":"US","androidSdkVersion":34}},"videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true,"params":"CgIQBg=="}
        """.trimIndent()
        return Exchange(
            url = "https://www.youtube.com/youtubei/v1/player?key=$PUBLIC_INNERTUBE_KEY&prettyPrint=false",
            method = "POST",
            headers = mapOf(
                "Content-Type" to "application/json",
                "User-Agent" to USER_AGENT,
                "X-YouTube-Client-Name" to "3",
                "X-YouTube-Client-Version" to CLIENT_VERSION,
                "Origin" to "https://www.youtube.com"
            ),
            body = body
        )
    }

    fun captionRequest(baseUrl: String): Exchange = Exchange(
        url = captionUrl(baseUrl),
        headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept-Language" to "en-US,en;q=0.9"
        )
    )

    fun captionUrl(baseUrl: String): String {
        val absolute = when {
            baseUrl.startsWith("//") -> "https:$baseUrl"
            baseUrl.startsWith("http://") || baseUrl.startsWith("https://") -> baseUrl
            else -> "https://www.youtube.com$baseUrl"
        }
        val withoutFmt = absolute
            .replace(Regex("([?&])fmt=[^&]*&?"), "$1")
            .replace(Regex("[?&]$"), "")
            .replace("?&", "?")
        val separator = if ('?' in withoutFmt) "&" else "?"
        return withoutFmt + separator + "fmt=json3"
    }

    fun title(playerJson: String): String = runCatching {
        root(playerJson)["videoDetails"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
    }.getOrNull().orEmpty().trim()

    fun selectTrack(playerJson: String): CaptionTrack? {
        val tracks = tracks(playerJson)
        if (tracks.isEmpty()) return null
        return tracks.firstOrNull { !it.generated && it.languageCode.startsWith("en") }
            ?: tracks.firstOrNull { !it.generated }
            ?: tracks.firstOrNull { it.languageCode.startsWith("en") }
            ?: tracks.first()
    }

    fun tracks(playerJson: String): List<CaptionTrack> = runCatching {
        val renderer = root(playerJson)["captions"]
            ?.jsonObject
            ?.get("playerCaptionsTracklistRenderer")
            ?.jsonObject
            ?: return emptyList()
        renderer["captionTracks"]?.jsonArray.orEmpty().mapNotNull { element ->
            val track = element.jsonObject
            val baseUrl = track["baseUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val language = track["languageCode"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val generated = track["kind"]?.jsonPrimitive?.contentOrNull == "asr"
            CaptionTrack(baseUrl, language, generated)
        }
    }.getOrDefault(emptyList())

    fun parseCaptions(body: String, maxChars: Int = MAX_CHARS): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return ""
        val text = if (trimmed.startsWith("{")) parseJson3(trimmed) else parseXml(trimmed)
        return text.take(maxChars).trim()
    }

    private fun parseJson3(body: String): String {
        val events = runCatching { root(body)["events"]?.jsonArray }.getOrNull() ?: return ""
        return events.mapNotNull { event ->
            val segs = event.jsonObject["segs"]?.jsonArray ?: return@mapNotNull null
            segs.joinToString(separator = "") { seg ->
                seg.jsonObject["utf8"]?.jsonPrimitive?.contentOrNull.orEmpty()
            }.trim().takeIf { it.isNotEmpty() }
        }.joinToString(separator = " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseXml(body: String): String = TEXT_NODE.findAll(body).map { match ->
        decodeEntities(match.groupValues[1])
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }.filter { it.isNotEmpty() }.joinToString(separator = " ")

    private fun root(body: String): JsonObject = json.parseToJsonElement(body).jsonObject

    private fun decodeEntities(value: String): String = value
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")

    private const val MAX_CHARS = 20_000

    private val TEXT_NODE = Regex("<text\\b[^>]*>([\\s\\S]*?)</text>", RegexOption.IGNORE_CASE)
}
