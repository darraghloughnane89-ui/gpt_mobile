package dev.chungjungsoo.gptmobile.data.model

enum class ClientType {
    OPENAI,
    ANTHROPIC,
    GOOGLE,
    GROQ,
    OPENROUTER,
    OLLAMA,
    CUSTOM,
    XAI,
    MIMO,
    LITERT_LM,
    LLAMA_CPP
}

fun ClientType.isOnDevice(): Boolean = this == ClientType.LITERT_LM || this == ClientType.LLAMA_CPP

fun ClientType.supportsRemoteModelList(): Boolean = !isOnDevice()

fun ClientType.usesOpenAiCompatibleChat(): Boolean = when (this) {
    ClientType.GROQ,
    ClientType.OLLAMA,
    ClientType.OPENROUTER,
    ClientType.CUSTOM,
    ClientType.XAI,
    ClientType.MIMO -> true

    else -> false
}

fun ClientType.usesOpenAiCompatibleBaseUrl(): Boolean = when (this) {
    ClientType.OPENAI,
    ClientType.GROQ,
    ClientType.OLLAMA,
    ClientType.OPENROUTER,
    ClientType.CUSTOM,
    ClientType.XAI,
    ClientType.MIMO -> true

    else -> false
}
