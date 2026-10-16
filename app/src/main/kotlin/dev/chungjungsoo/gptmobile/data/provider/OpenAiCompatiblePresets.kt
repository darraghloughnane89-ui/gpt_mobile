package dev.chungjungsoo.gptmobile.data.provider

import dev.chungjungsoo.gptmobile.data.ModelConstants

data class EndpointPreset(
    val label: String,
    val apiUrl: String
)

object OpenAiCompatiblePresets {
    val shortcuts: List<EndpointPreset> = listOf(
        EndpointPreset("OpenRouter", ModelConstants.OPENROUTER_API_URL),
        EndpointPreset("Groq", ModelConstants.GROQ_API_URL),
        EndpointPreset("xAI", ModelConstants.XAI_API_URL),
        EndpointPreset("MiMo Europe", ModelConstants.MIMO_API_URL),
        EndpointPreset("MiMo Singapore", ModelConstants.MIMO_API_URL_SINGAPORE),
        EndpointPreset("MiMo China", ModelConstants.MIMO_API_URL_CHINA),
        EndpointPreset("MiMo API", ModelConstants.MIMO_PAYG_API_URL),
        EndpointPreset("Ollama", ModelConstants.OLLAMA_API_URL),
        EndpointPreset("LM Studio", "http://127.0.0.1:1234/v1/"),
        EndpointPreset("vLLM", "http://127.0.0.1:8000/v1/"),
        EndpointPreset("llama.cpp", "http://127.0.0.1:8080/v1/")
    )

    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val withScheme = trimmed
        if (!withScheme.startsWith("http://") && !withScheme.startsWith("https://")) {
            return withScheme
        }
        val noTrailing = withScheme.trimEnd('/')
        val slash = noTrailing.indexOf('/', startIndex = withScheme.indexOf("://") + 3)
        val path = if (slash == -1) "" else noTrailing.substring(slash)
        val origin = if (slash == -1) noTrailing else noTrailing.substring(0, slash)
        val normalizedPath = when {
            path.isEmpty() || path == "/" -> "/v1"
            path.endsWith("/v1") -> path
            else -> path
        }
        return "$origin$normalizedPath/"
    }
}
