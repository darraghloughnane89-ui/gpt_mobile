package dev.chungjungsoo.gptmobile.data.hermes

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun isValidHermesProfile(profile: String): Boolean = HermesDefaults.PROFILE_PATTERN.matches(profile)

fun parseHermesEndpoint(raw: String): HermesEndpoint? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
    val uri = try {
        URI(withScheme)
    } catch (_: URISyntaxException) {
        return null
    }
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host ?: return null
    if (host.isBlank() || uri.userInfo != null) return null

    var path = uri.rawPath.orEmpty().trimEnd('/')
    if (path == "/") path = ""
    var profileHint: String? = null
    listOf("/chat/completions", "/models", "/capabilities", "/health").forEach { suffix ->
        if (path.endsWith(suffix)) path = path.removeSuffix(suffix)
    }
    if (path.endsWith("/v1")) path = path.removeSuffix("/v1")
    val multiplex = MULTIPLEX_SUFFIX.find(path)
    if (multiplex != null) {
        profileHint = URLDecoder.decode(multiplex.groupValues[1], StandardCharsets.UTF_8)
        path = path.removeSuffix(multiplex.value)
    }
    if (path == "/") path = ""
    val port = if (uri.port == -1) "" else ":${uri.port}"
    val base = "$scheme://$host$port$path".trimEnd('/')
    return HermesEndpoint(base = base, profileHint = profileHint?.takeIf { it.isNotBlank() })
}

fun directApiRoot(base: String): String = "${base.trimEnd('/')}/v1"

fun multiplexApiRoot(base: String, profile: String): String {
    val encoded = URLEncoder.encode(profile, StandardCharsets.UTF_8).replace("+", "%20")
    return "${base.trimEnd('/')}/p/$encoded/v1"
}

fun hermesModelId(profile: String, advertised: String?): String = advertised?.takeIf { it.isNotBlank() }
    ?: profile.takeIf { it.isNotBlank() }
    ?: HermesDefaults.MODEL_FALLBACK

private val MULTIPLEX_SUFFIX = Regex("/p/([^/]+)$")
