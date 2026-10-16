package dev.chungjungsoo.gptmobile.data

import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.ClientType

object ModelConstants {
    // LinkedHashSet should be used to guarantee item order
    const val OPENAI_DEFAULT_MODEL = "gpt-5.6"
    const val ANTHROPIC_DEFAULT_MODEL = "claude-sonnet-5"
    const val GOOGLE_DEFAULT_MODEL = "gemini-3.7-flash"
    const val GROQ_DEFAULT_MODEL = "openai/gpt-oss-120b"
    const val OLLAMA_DEFAULT_MODEL = "gpt-oss"

    // OpenRouter has no unsuffixed alias; only the explicit tier slugs are served.
    const val OPENROUTER_DEFAULT_MODEL = "openai/gpt-5.6-sol"

    // xAI docs (https://docs.x.ai/developers/models, checked 2026-10-15): grok-4.7 is the
    // flagship chat and code model. grok-4.6 is the previous flagship. grok-4.3 is the
    // cheaper 1M-context text model. Image generation ids (grok-imagine-*) are not chat defaults.
    const val XAI_DEFAULT_MODEL = "grok-4.7"

    // Xiaomi MiMo docs (https://mimo.mi.com/docs/en-US/quick-start/summary/model, checked 2026-10-15).
    // Token Plan Europe is the selectable default; China, Singapore, and the pay-as-you-go host are presets.
    const val MIMO_DEFAULT_MODEL = "mimo-v2.6-pro"

    val openaiModels = linkedSetOf(OPENAI_DEFAULT_MODEL, "gpt-5.4", "gpt-5.4-mini", "gpt-5.4-nano")
    val anthropicModels = linkedSetOf(ANTHROPIC_DEFAULT_MODEL, "claude-opus-5", "claude-sonnet-4-6", "claude-haiku-4-5-20251001")
    val googleModels = linkedSetOf(GOOGLE_DEFAULT_MODEL, "gemini-3.1-pro-preview", "gemini-3-flash-preview", "gemini-2.5-flash")
    val groqModels = linkedSetOf(GROQ_DEFAULT_MODEL, "openai/gpt-oss-20b", "qwen/qwen3.6-27b", "llama-3.3-70b-versatile")
    val ollamaModels = linkedSetOf(OLLAMA_DEFAULT_MODEL)
    val xaiModels = linkedSetOf(XAI_DEFAULT_MODEL, "grok-4.6", "grok-4.3")
    val mimoModels = linkedSetOf(MIMO_DEFAULT_MODEL, "mimo-v2.6-flash", "mimo-v2.5-pro")

    const val OPENAI_API_URL = "https://api.openai.com/v1/"
    const val ANTHROPIC_API_URL = "https://api.anthropic.com/v1/"
    const val GOOGLE_API_URL = "https://generativelanguage.googleapis.com/"
    const val GROQ_API_URL = "https://api.groq.com/openai/v1/"
    const val OPENROUTER_API_URL = "https://openrouter.ai/api/v1/"
    const val OLLAMA_API_URL = "http://localhost:11434/v1/"
    const val XAI_API_URL = "https://api.x.ai/v1/"
    const val MIMO_API_URL = "https://token-plan-ams.xiaomimimo.com/v1/"
    const val MIMO_API_URL_SINGAPORE = "https://token-plan-sgp.xiaomimimo.com/v1/"
    const val MIMO_API_URL_CHINA = "https://token-plan-cn.xiaomimimo.com/v1/"
    const val MIMO_PAYG_API_URL = "https://api.xiaomimimo.com/v1/"

    fun getDefaultAPIUrl(apiType: ApiType) = when (apiType) {
        ApiType.OPENAI -> OPENAI_API_URL
        ApiType.ANTHROPIC -> ANTHROPIC_API_URL
        ApiType.GOOGLE -> GOOGLE_API_URL
        ApiType.GROQ -> GROQ_API_URL
        ApiType.OLLAMA -> ""
    }

    fun normalizeLegacyAPIUrl(apiUrl: String): String = when (apiUrl.trim()) {
        "https://api.openai.com", "https://api.openai.com/" -> OPENAI_API_URL
        "https://api.anthropic.com", "https://api.anthropic.com/" -> ANTHROPIC_API_URL
        "https://generativelanguage.googleapis.com", "https://generativelanguage.googleapis.com/" -> GOOGLE_API_URL
        "https://api.groq.com/openai", "https://api.groq.com/openai/" -> GROQ_API_URL
        "https://openrouter.ai/api", "https://openrouter.ai/api/" -> OPENROUTER_API_URL
        "http://localhost:11434", "http://localhost:11434/" -> OLLAMA_API_URL
        else -> apiUrl
    }

    fun defaultPlatformName(clientType: ClientType): String = when (clientType) {
        ClientType.OPENAI -> "OpenAI"
        ClientType.ANTHROPIC -> "Anthropic"
        ClientType.GOOGLE -> "Google"
        ClientType.GROQ -> "Groq"
        ClientType.OLLAMA -> "Ollama"
        ClientType.OPENROUTER -> "OpenRouter"
        ClientType.CUSTOM -> ""
        ClientType.XAI -> "Grok"
        ClientType.MIMO -> "MiMo"
        ClientType.LITERT_LM -> "Local"
        ClientType.LLAMA_CPP -> "GGUF"
    }

    fun defaultApiUrl(clientType: ClientType): String = when (clientType) {
        ClientType.OPENAI -> OPENAI_API_URL
        ClientType.ANTHROPIC -> ANTHROPIC_API_URL
        ClientType.GOOGLE -> GOOGLE_API_URL
        ClientType.GROQ -> GROQ_API_URL
        ClientType.OLLAMA -> OLLAMA_API_URL
        ClientType.OPENROUTER -> OPENROUTER_API_URL
        ClientType.CUSTOM -> ""
        ClientType.XAI -> XAI_API_URL
        ClientType.MIMO -> MIMO_API_URL
        ClientType.LITERT_LM -> ""
        ClientType.LLAMA_CPP -> ""
    }

    fun builtinModelIds(clientType: ClientType): Set<String> = when (clientType) {
        ClientType.OPENAI -> openaiModels
        ClientType.ANTHROPIC -> anthropicModels
        ClientType.GOOGLE -> googleModels
        ClientType.GROQ -> groqModels
        ClientType.OLLAMA -> ollamaModels
        ClientType.OPENROUTER -> linkedSetOf(OPENROUTER_DEFAULT_MODEL)
        ClientType.CUSTOM -> emptySet()
        ClientType.XAI -> xaiModels
        ClientType.MIMO -> mimoModels
        ClientType.LITERT_LM -> emptySet()
        ClientType.LLAMA_CPP -> emptySet()
    }

    fun defaultModel(clientType: ClientType): String = when (clientType) {
        ClientType.OPENAI -> OPENAI_DEFAULT_MODEL
        ClientType.ANTHROPIC -> ANTHROPIC_DEFAULT_MODEL
        ClientType.GOOGLE -> GOOGLE_DEFAULT_MODEL
        ClientType.GROQ -> GROQ_DEFAULT_MODEL
        ClientType.OLLAMA -> OLLAMA_DEFAULT_MODEL
        ClientType.OPENROUTER -> OPENROUTER_DEFAULT_MODEL
        ClientType.CUSTOM -> ""
        ClientType.XAI -> XAI_DEFAULT_MODEL
        ClientType.MIMO -> MIMO_DEFAULT_MODEL
        ClientType.LITERT_LM -> ""
        ClientType.LLAMA_CPP -> ""
    }

    const val ANTHROPIC_MAXIMUM_TOKEN = 4096

    const val OPENAI_PROMPT =
        "You are a helpful, clever, and very friendly assistant. " +
            "You are familiar with various languages in the world. " +
            "You are to answer my questions precisely. "

    const val DEFAULT_PROMPT = "Your task is to answer my questions precisely."

    const val CHAT_TITLE_GENERATE_PROMPT =
        "Create a title that summarizes the chat. " +
            "The output must match the language that the user and the opponent is using, and should be less than 50 letters. " +
            "The output should only include the sentence in plain text without bullets or double asterisks. Do not use markdown syntax.\n" +
            "[Chat Content]\n"
}
