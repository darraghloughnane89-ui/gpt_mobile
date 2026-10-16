package dev.chungjungsoo.gptmobile.data.security

enum class SecretType {
    API_KEY,
    OAUTH_TOKEN,
    PLUGIN_CREDENTIAL,
    PASSWORD
}

data class SecretEntry(
    val id: String,
    val type: SecretType,
    val label: String,
    val masked: String = "",
    val updatedAtEpochMs: Long = 0L,
    val endpoint: String = ""
)

object SecretMask {
    fun of(secret: ByteArray): String {
        if (secret.size <= 8) return "••••"
        val prefix = secret.copyOfRange(0, 3).decodeToString()
        val suffix = secret.copyOfRange(secret.size - 4, secret.size).decodeToString()
        return "$prefix…$suffix"
    }
}

object VaultKeyPolicy {
    const val AUTH_VALIDITY_SECONDS = 5 * 60

    fun attempts(strongBox: Boolean, userAuth: Boolean): List<Pair<Boolean, Boolean>> = buildList {
        add(strongBox to userAuth)
        if (strongBox) add(false to userAuth)
        if (userAuth) add(false to false)
    }.distinct()
}
