package dev.chungjungsoo.gptmobile.data.hermes

import com.sun.net.httpserver.HttpServer
import dev.chungjungsoo.gptmobile.data.database.dao.ChatPlatformModelV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatPlatformModelV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.datastore.SettingDataSource
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPIImpl
import dev.chungjungsoo.gptmobile.data.network.ProviderRequestConfig
import dev.chungjungsoo.gptmobile.data.network.hermesChatRequestConfig
import dev.chungjungsoo.gptmobile.data.repository.SecretMigrationError
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepositoryImpl
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import io.ktor.client.engine.cio.CIO
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HermesChatSecretTest {
    @Test
    fun sync_storesVaultReferenceAndNeverCopiesKeyIntoPlatformToken() = runBlocking {
        val repository = RecordingSettings()
        repository.platforms += hermesPlatform(id = 2, token = "sk-already-on-platform", secretRef = null)
        HermesPlatformSync(repository).sync(
            HermesSettings(endpoint = "http://100.64.1.8:8642", chatModelEnabled = true),
            "sk-passed-into-sync"
        )

        val saved = repository.updated.single()
        assertNull(saved.token)
        assertEquals(HermesDefaults.SECRET_REF, saved.secretRef)
        assertFalse(saved.toString().contains("sk-passed-into-sync"))
        assertFalse(saved.toString().contains("sk-already-on-platform"))
    }

    @Test
    fun sync_disabledChat_doesNotWriteTheKeyOntoThePlatform() = runBlocking {
        val repository = RecordingSettings()
        repository.platforms += hermesPlatform(id = 2, token = "sk-already-on-platform", secretRef = HermesDefaults.SECRET_REF)
        HermesPlatformSync(repository).sync(
            HermesSettings(endpoint = "http://100.64.1.8:8642", chatModelEnabled = false),
            "sk-passed-into-sync"
        )

        val saved = repository.updated.single()
        assertNull(saved.token)
        assertEquals(HermesDefaults.SECRET_REF, saved.secretRef)
        assertFalse(saved.enabled)
    }

    @Test
    fun fetchAndUpdate_keepHermesKeyInVaultAndOffPlatformToken() = runBlocking {
        val vault = MapVault()
        vault.put(HermesDefaults.SECRET_REF, "sk-real-key".encodeToByteArray())
        val dao = MemoryPlatformDao()
        dao.rows += hermesPlatform(id = 3, token = null, secretRef = HermesDefaults.SECRET_REF)
        val repository = SettingRepositoryImpl(UnusedSettings(), dao, UnusedChatModels(), vault)

        val fetched = repository.fetchPlatformV2s().single()
        assertNull(fetched.token)
        assertEquals(HermesDefaults.SECRET_REF, fetched.secretRef)

        repository.updatePlatformV2(fetched.copy(token = "sk-copied-from-caller", enabled = true))
        val stored = dao.rows.single()
        assertNull(stored.token)
        assertEquals(HermesDefaults.SECRET_REF, stored.secretRef)
        assertEquals("sk-real-key", vault.read(HermesDefaults.SECRET_REF)!!.decodeToString())

        repository.deletePlatformV2(stored)
        assertNotNull(vault.read(HermesDefaults.SECRET_REF))
        assertEquals("sk-real-key", vault.read(HermesDefaults.SECRET_REF)!!.decodeToString())
    }

    @Test
    fun hermesChatRequestConfig_dropsCopiedPlatformToken() {
        val config = hermesPlatform(id = 1, token = "sk-copied-into-platform", secretRef = HermesDefaults.SECRET_REF)
            .hermesChatRequestConfig()
        assertNull(config.token)
        assertEquals(HermesDefaults.SECRET_REF, config.secretRef)
    }

    @Test
    fun chatCompletion_injectsVaultKeyAtHttpLayerOnly() = runBlocking(Dispatchers.IO) {
        val secret = "sk-hermes-vault-key"
        val vault = MapVault()
        vault.put(HermesDefaults.SECRET_REF, secret.encodeToByteArray())
        val seen = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            seen.set(exchange.requestHeaders.getFirst("Authorization"))
            exchange.requestBody.use { it.readBytes() }
            val bytes = "data: {\"id\":\"chat_1\",\"choices\":[]}\n\n".toByteArray()
            exchange.responseHeaders.set("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val network = NetworkClient(CIO, vault)
        try {
            val config = ProviderRequestConfig(
                apiUrl = "http://127.0.0.1:${server.address.port}/",
                token = null,
                secretRef = HermesDefaults.SECRET_REF
            )
            OpenAIAPIImpl(network).streamChatCompletion(
                ChatCompletionRequest("hermes-agent", emptyList()),
                5,
                config
            ).toList()

            assertNull(config.token)
            assertEquals("Bearer $secret", seen.get())
            assertFalse(seen.get().orEmpty() == "Bearer ")
        } finally {
            network().close()
            server.stop(0)
        }
    }
}

private fun hermesPlatform(id: Int, token: String?, secretRef: String?) = PlatformV2(
    id = id,
    uid = HermesDefaults.PLATFORM_UID,
    name = HermesDefaults.PLATFORM_NAME,
    compatibleType = ClientType.CUSTOM,
    enabled = true,
    apiUrl = "http://100.64.1.8:8642/v1/",
    token = token,
    secretRef = secretRef,
    model = HermesDefaults.MODEL_FALLBACK
)

private class MapVault : SecretVault {
    val values = mutableMapOf<String, ByteArray>()

    override suspend fun put(secretRef: String, secret: ByteArray) {
        values[secretRef] = secret.copyOf()
    }

    override suspend fun read(secretRef: String): ByteArray? = values[secretRef]?.copyOf()

    override suspend fun delete(secretRef: String) {
        values.remove(secretRef)
    }
}

private class RecordingSettings : SettingRepository {
    val platforms = mutableListOf<PlatformV2>()
    val updated = mutableListOf<PlatformV2>()

    override suspend fun fetchPlatformV2s(): List<PlatformV2> = platforms.toList()

    override suspend fun addPlatformV2(platform: PlatformV2) {
        updated += platform
    }

    override suspend fun updatePlatformV2(platform: PlatformV2) {
        updated += platform
    }

    override suspend fun fetchPlatforms(): List<Platform> = error("unused")

    override suspend fun fetchThemes(): ThemeSetting = error("unused")

    override suspend fun migrateToPlatformV2() = error("unused")

    override suspend fun migrateSecrets(): List<SecretMigrationError> = error("unused")

    override suspend fun updatePlatforms(platforms: List<Platform>) = error("unused")

    override suspend fun updateThemes(themeSetting: ThemeSetting) = error("unused")

    override suspend fun deletePlatformV2(platform: PlatformV2) = error("unused")

    override suspend fun getPlatformV2ById(id: Int): PlatformV2? = error("unused")
}

private class MemoryPlatformDao : PlatformV2Dao {
    val rows = mutableListOf<PlatformV2>()

    override suspend fun getPlatforms(): List<PlatformV2> = rows.toList()

    override suspend fun getPlatform(id: Int): PlatformV2? = rows.firstOrNull { it.id == id }

    override suspend fun addPlatform(platform: PlatformV2): Long {
        rows += platform
        return platform.id.toLong()
    }

    override suspend fun editPlatform(platform: PlatformV2) {
        val index = rows.indexOfFirst { it.id == platform.id }
        if (index >= 0) rows[index] = platform else rows += platform
    }

    override suspend fun deleteBindingsByProfileUid(profileUid: String) = Unit

    override suspend fun deletePlatformRow(platform: PlatformV2) {
        rows.removeAll { it.uid == platform.uid }
    }
}

private class UnusedChatModels : ChatPlatformModelV2Dao {
    override suspend fun getByChatId(chatId: Int): List<ChatPlatformModelV2> = emptyList()

    override suspend fun upsertAll(vararg models: ChatPlatformModelV2) = Unit

    override suspend fun deleteByChatId(chatId: Int) = Unit

    override suspend fun deleteByPlatformUid(platformUid: String) = Unit
}

private class UnusedSettings : SettingDataSource {
    override suspend fun updateDynamicTheme(theme: DynamicTheme) = unused()

    override suspend fun updateThemeMode(themeMode: ThemeMode) = unused()

    override suspend fun updateStatus(apiType: ApiType, status: Boolean) = unused()

    override suspend fun updateAPIUrl(apiType: ApiType, url: String) = unused()

    override suspend fun updateToken(apiType: ApiType, token: String) = unused()

    override suspend fun clearToken(apiType: ApiType) = unused()

    override suspend fun updateModel(apiType: ApiType, model: String) = unused()

    override suspend fun updateTemperature(apiType: ApiType, temperature: Float) = unused()

    override suspend fun updateTopP(apiType: ApiType, topP: Float) = unused()

    override suspend fun updateSystemPrompt(apiType: ApiType, prompt: String) = unused()

    override suspend fun getDynamicTheme(): DynamicTheme? = unused()

    override suspend fun getThemeMode(): ThemeMode? = unused()

    override suspend fun getStatus(apiType: ApiType): Boolean? = unused()

    override suspend fun getAPIUrl(apiType: ApiType): String? = unused()

    override suspend fun getToken(apiType: ApiType): String? = unused()

    override suspend fun getModel(apiType: ApiType): String? = unused()

    override suspend fun getTemperature(apiType: ApiType): Float? = unused()

    override suspend fun getTopP(apiType: ApiType): Float? = unused()

    override suspend fun getSystemPrompt(apiType: ApiType): String? = unused()

    private fun unused(): Nothing = error("unused")
}
