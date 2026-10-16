package dev.chungjungsoo.gptmobile.data.drive

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DriveHttpException(val status: Int) : Exception("Drive request failed ($status).")

@Singleton
class GoogleDriveAppData @Inject constructor() : DriveAppDataStore {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun findBackup(accessToken: String): String? = withClient {
        val response = get("https://www.googleapis.com/drive/v3/files") {
            header(HttpHeaders.Authorization, bearer(accessToken))
            parameter("spaces", "appDataFolder")
            parameter("q", "name = '${DriveBackupSpec.FILE_NAME}'")
            parameter("fields", "files(id,name)")
        }
        if (!response.status.isSuccess()) throw DriveHttpException(response.status.value)
        json.decodeFromString<DriveFileList>(response.bodyAsText())
            .files
            .firstOrNull { it.id.isNotBlank() }
            ?.id
    }

    override suspend fun upload(
        accessToken: String,
        existingFileId: String?,
        bytes: ByteArray
    ): String = withClient {
        val response = if (existingFileId.isNullOrBlank()) {
            val boundary = "gptmobileboundary"
            post("https://www.googleapis.com/upload/drive/v3/files") {
                header(HttpHeaders.Authorization, bearer(accessToken))
                header(HttpHeaders.ContentType, "multipart/related; boundary=$boundary")
                parameter("uploadType", "multipart")
                setBody(multipart(boundary, bytes))
            }
        } else {
            patch("https://www.googleapis.com/upload/drive/v3/files/$existingFileId") {
                header(HttpHeaders.Authorization, bearer(accessToken))
                header(HttpHeaders.ContentType, "application/octet-stream")
                parameter("uploadType", "media")
                setBody(bytes)
            }
        }
        if (!response.status.isSuccess()) throw DriveHttpException(response.status.value)
        val id = json.decodeFromString<DriveFileResource>(response.bodyAsText()).id
        if (id.isBlank()) throw DriveHttpException(response.status.value)
        id
    }

    override suspend fun download(accessToken: String, fileId: String): ByteArray = withClient {
        val response = get("https://www.googleapis.com/drive/v3/files/$fileId") {
            header(HttpHeaders.Authorization, bearer(accessToken))
            parameter("alt", "media")
        }
        if (!response.status.isSuccess()) throw DriveHttpException(response.status.value)
        response.bodyAsBytes()
    }

    private fun bearer(accessToken: String) = "Bearer $accessToken"

    private fun multipart(boundary: String, payload: ByteArray): ByteArray {
        val metadata = """{"name":"${DriveBackupSpec.FILE_NAME}","parents":["appDataFolder"]}"""
        val preamble = (
            "--$boundary\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                metadata +
                "\r\n--$boundary\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n"
            ).encodeToByteArray()
        val ending = "\r\n--$boundary--\r\n".encodeToByteArray()
        return preamble + payload + ending
    }

    private suspend fun <T> withClient(block: suspend HttpClient.() -> T): T {
        val client = HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 60_000
            }
        }
        return try {
            client.block()
        } finally {
            client.close()
        }
    }

    @Serializable
    private data class DriveFileResource(val id: String = "", val name: String = "")

    @Serializable
    private data class DriveFileList(val files: List<DriveFileResource> = emptyList())
}
