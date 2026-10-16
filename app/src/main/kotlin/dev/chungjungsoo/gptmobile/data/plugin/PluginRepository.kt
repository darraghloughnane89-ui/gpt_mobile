package dev.chungjungsoo.gptmobile.data.plugin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.agent.tool.McpClientManager
import dev.chungjungsoo.gptmobile.data.agent.tool.McpClientTransport
import dev.chungjungsoo.gptmobile.data.agent.tool.McpConnectionConfig
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthClient
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthCredential
import dev.chungjungsoo.gptmobile.data.agent.tool.namespaceMcpToolName
import dev.chungjungsoo.gptmobile.data.database.dao.PluginRoutineDao
import dev.chungjungsoo.gptmobile.data.database.entity.PluginEntity
import dev.chungjungsoo.gptmobile.data.database.entity.PluginScopeSelectionEntity
import dev.chungjungsoo.gptmobile.data.database.entity.PluginToolEntity
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class PluginCredentialStore @Inject constructor(
    private val vault: SecretVault,
    private val oauthClient: McpOAuthClient
) {
    suspend fun saveBearer(pluginUid: String, token: String) {
        val header = PluginOAuthFlow(oauthClient).bearerHeader(token)
        val tokenOnly = header.removePrefix("Bearer ")
        write(secretRef(pluginUid), tokenOnly)
    }

    suspend fun saveOAuth(pluginUid: String, credential: McpOAuthCredential) {
        write(secretRef(pluginUid), NetworkClient.json.encodeToString(credential))
    }

    suspend fun saveLaunch(pluginUid: String, spec: AppAuthLaunchSpec) {
        write(pendingRef(pluginUid), NetworkClient.json.encodeToString(spec))
    }

    suspend fun readLaunch(pluginUid: String): AppAuthLaunchSpec? = read(pendingRef(pluginUid))

    suspend fun deletePending(pluginUid: String) {
        vault.delete(pendingRef(pluginUid))
    }

    suspend fun authorizationHeader(plugin: PluginEntity): String? = when (plugin.authType) {
        PluginAuth.NONE -> null
        PluginAuth.BEARER -> {
            val token = readRaw(secretRef(plugin.pluginUid)) ?: throw IllegalStateException("Bearer token is missing.")
            "Bearer $token"
        }
        PluginAuth.OAUTH -> {
            val credential = read<McpOAuthCredential>(secretRef(plugin.pluginUid))
                ?: throw IllegalStateException("OAuth sign-in is not finished.")
            val refreshed = if (credential.expiresAtEpochSeconds != null && credential.expiresAtEpochSeconds <= now() + 60) {
                oauthClient.refresh(credential).also { saveOAuth(plugin.pluginUid, it) }
            } else {
                credential
            }
            "${refreshed.tokenType} ${refreshed.accessToken}"
        }
        else -> throw IllegalArgumentException("Unsupported plugin authentication.")
    }

    suspend fun delete(pluginUid: String) {
        vault.delete(secretRef(pluginUid))
        vault.delete(pendingRef(pluginUid))
    }

    private suspend fun write(ref: String, value: String) {
        val bytes = value.encodeToByteArray()
        try {
            vault.put(ref, bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private suspend inline fun <reified T> read(ref: String): T? {
        val raw = readRaw(ref) ?: return null
        return NetworkClient.json.decodeFromString<T>(raw)
    }

    private suspend fun readRaw(ref: String): String? {
        val bytes = vault.read(ref) ?: return null
        return try {
            bytes.decodeToString()
        } finally {
            bytes.fill(0)
        }
    }

    private fun now(): Long = System.currentTimeMillis() / 1000

    companion object {
        fun secretRef(pluginUid: String) = "plugin_secret_$pluginUid"
        fun pendingRef(pluginUid: String) = "plugin_oauth_pending_$pluginUid"
    }
}

@Singleton
class PluginRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: PluginRoutineDao,
    private val credentials: PluginCredentialStore,
    private val mcpClient: McpClientManager,
    private val oauthFlow: PluginOAuthFlow
) {
    fun observePlugins(): Flow<List<PluginEntity>> = dao.observePlugins()

    fun observeTools(pluginUid: String): Flow<List<PluginToolEntity>> = dao.observeTools(pluginUid)

    suspend fun tools(pluginUid: String): List<PluginToolEntity> = dao.listTools(pluginUid)

    suspend fun listPlugins(): List<PluginEntity> = dao.listPlugins()

    suspend fun selections(): List<PluginScopeSelectionEntity> = dao.listSelections()

    suspend fun installPreset(
        preset: PluginPreset,
        endpointUrl: String = preset.endpointUrl,
        bearerToken: String? = null
    ): PluginEntity {
        val url = endpointUrl.trim()
        if (preset.needsServer && url.isEmpty()) {
            throw IllegalArgumentException("Enter the MCP server URL. This app does not include a server for ${preset.name}.")
        }
        require(url.isNotEmpty()) { "Plugin URL is required." }
        val now = now()
        val uid = "plugin-${preset.id}"
        val authType = when {
            preset.authType == PluginAuth.BEARER && bearerToken.isNullOrBlank() && preset.allowsBearerFallback -> PluginAuth.NONE
            else -> preset.authType
        }
        val plugin = PluginEntity(
            pluginUid = uid,
            catalogId = preset.id,
            name = preset.name,
            endpointUrl = endpointUrl.trim(),
            transport = preset.transport,
            authType = authType,
            secretRef = if (authType == PluginAuth.NONE) null else PluginCredentialStore.secretRef(uid),
            oauthClientId = null,
            allowCleartext = preset.allowCleartext || url.startsWith("http://"),
            enabled = true,
            status = if (authType == PluginAuth.NONE) PluginStatus.DISCONNECTED else PluginStatus.NEEDS_AUTH,
            statusDetail = null,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertPlugin(plugin)
        if (!bearerToken.isNullOrBlank()) {
            credentials.saveBearer(uid, bearerToken)
            dao.updateCredential(uid, null, PluginCredentialStore.secretRef(uid), now)
        }
        if (authType != PluginAuth.OAUTH) {
            refresh(uid)
        }
        return dao.getPlugin(uid) ?: plugin
    }

    suspend fun installCustom(
        name: String,
        endpointUrl: String,
        transport: String,
        authType: String,
        bearerToken: String?,
        allowCleartext: Boolean
    ): PluginEntity {
        val uid = "plugin-" + UUID.randomUUID().toString().replace("-", "").take(16)
        val now = now()
        val plugin = PluginEntity(
            pluginUid = uid,
            catalogId = null,
            name = name.trim(),
            endpointUrl = endpointUrl.trim(),
            transport = transport,
            authType = authType,
            secretRef = if (authType == PluginAuth.NONE) null else PluginCredentialStore.secretRef(uid),
            oauthClientId = null,
            allowCleartext = allowCleartext,
            enabled = true,
            status = PluginStatus.DISCONNECTED,
            statusDetail = null,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertPlugin(plugin)
        if (!bearerToken.isNullOrBlank()) credentials.saveBearer(uid, bearerToken)
        if (authType != PluginAuth.OAUTH) refresh(uid)
        return dao.getPlugin(uid) ?: plugin
    }

    suspend fun setEnabled(pluginUid: String, enabled: Boolean) {
        dao.updateEnabled(pluginUid, enabled, now())
    }

    suspend fun setToolEnabled(toolUid: String, enabled: Boolean) {
        dao.updateToolEnabled(toolUid, enabled)
    }

    suspend fun delete(pluginUid: String) {
        credentials.delete(pluginUid)
        mcpClient.close(pluginUid)
        dao.deletePlugin(pluginUid)
    }

    suspend fun setScope(scopeType: String, scopeId: String, pluginUid: String, selected: Boolean) {
        if (selected) {
            dao.upsertSelection(
                PluginScopeSelectionEntity(
                    selectionUid = "$scopeType:$scopeId:$pluginUid",
                    scopeType = scopeType,
                    scopeId = scopeId,
                    pluginUid = pluginUid
                )
            )
        } else {
            dao.deleteSelection(scopeType, scopeId, pluginUid)
        }
    }

    suspend fun beginOAuth(pluginUid: String): AppAuthLaunchSpec {
        val plugin = dao.getPlugin(pluginUid) ?: throw IllegalArgumentException("Plugin was not found.")
        val (discovery, start) = oauthFlow.begin(
            resourceUrl = plugin.endpointUrl,
            allowCleartext = plugin.allowCleartext,
            redirectUri = pluginOAuthRedirectUri(pluginUid),
            suppliedClientId = plugin.oauthClientId
        )
        val spec = oauthFlow.launchSpec(discovery, start)
        credentials.saveLaunch(pluginUid, spec)
        dao.updateCredential(pluginUid, start.pending.clientId, PluginCredentialStore.secretRef(pluginUid), now())
        return spec
    }

    suspend fun completeOAuth(callbackUri: String) {
        val pluginUid = pluginOAuthPluginUid(callbackUri) ?: return
        val spec = credentials.readLaunch(pluginUid) ?: return
        val credential = PluginOAuthBrowser.exchange(context, spec, callbackUri)
        credentials.saveOAuth(pluginUid, credential)
        credentials.deletePending(pluginUid)
        dao.updateCredential(pluginUid, credential.clientId, PluginCredentialStore.secretRef(pluginUid), now())
        mcpClient.close(pluginUid)
        refresh(pluginUid)
    }

    suspend fun refresh(pluginUid: String) {
        val plugin = dao.getPlugin(pluginUid) ?: return
        try {
            val endpoint = endpoint(plugin)
            val tools = mcpClient.listTools(endpoint)
            val alias = plugin.catalogId ?: plugin.pluginUid
            dao.deleteTools(plugin.pluginUid)
            dao.insertTools(
                tools.map { tool ->
                    val effect = classifyToolEffect(
                        name = tool.name,
                        description = tool.description.orEmpty(),
                        readOnlyHint = tool.annotations?.readOnlyHint,
                        destructiveHint = tool.annotations?.destructiveHint
                    )
                    PluginToolEntity(
                        toolUid = UUID.randomUUID().toString(),
                        pluginUid = plugin.pluginUid,
                        name = tool.name,
                        modelName = namespaceMcpToolName(alias, tool.name),
                        description = tool.description.orEmpty().take(4_000),
                        inputSchemaJson = tool.inputSchema.toString(),
                        effect = effect.name,
                        enabled = effect == ToolEffect.READ
                    )
                }
            )
            dao.updateStatus(plugin.pluginUid, PluginStatus.CONNECTED, null, now())
        } catch (error: Exception) {
            val needsAuth = plugin.authType != PluginAuth.NONE && error.message?.contains("401") == true
            dao.updateStatus(
                plugin.pluginUid,
                if (needsAuth || plugin.authType == PluginAuth.OAUTH && plugin.secretRef != null && error.message?.contains("not finished") == true) {
                    PluginStatus.NEEDS_AUTH
                } else if (plugin.authType != PluginAuth.NONE && error.message?.contains("missing") == true) {
                    PluginStatus.NEEDS_AUTH
                } else {
                    PluginStatus.ERROR
                },
                error.message?.take(300),
                now()
            )
        }
    }

    suspend fun endpoint(plugin: PluginEntity): McpConnectionConfig = McpConnectionConfig(
        connectionUid = plugin.pluginUid,
        endpointUrl = plugin.endpointUrl,
        allowCleartext = plugin.allowCleartext,
        authorizationHeader = credentials.authorizationHeader(plugin),
        transport = if (plugin.transport == PluginTransport.SSE) McpClientTransport.SSE else McpClientTransport.STREAMABLE_HTTP
    )

    private fun now(): Long = System.currentTimeMillis() / 1000
}
