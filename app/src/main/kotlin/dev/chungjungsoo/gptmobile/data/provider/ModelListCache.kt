package dev.chungjungsoo.gptmobile.data.provider

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CachedModelList(
    val fetchedAtEpochMs: Long,
    val models: List<RemoteModel>
)

class ModelListCache(
    private val directory: File,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun read(clientTypeName: String, apiUrl: String): CachedModelList? {
        val file = fileFor(clientTypeName, apiUrl)
        if (!file.isFile) return null
        return runCatching { json.decodeFromString<CachedModelList>(file.readText()) }.getOrNull()
    }

    fun write(clientTypeName: String, apiUrl: String, cached: CachedModelList) {
        directory.mkdirs()
        fileFor(clientTypeName, apiUrl).writeText(json.encodeToString(cached))
    }

    private fun fileFor(clientTypeName: String, apiUrl: String): File {
        val key = sha256("$clientTypeName|$apiUrl")
        return File(directory, "$key.json")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
