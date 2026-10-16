package dev.chungjungsoo.gptmobile.data.bot

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.database.dao.AgentToolBindingWithConnection
import dev.chungjungsoo.gptmobile.data.database.dao.BotDao
import dev.chungjungsoo.gptmobile.data.database.dao.ToolConnectionDao
import dev.chungjungsoo.gptmobile.data.database.entity.Bot
import dev.chungjungsoo.gptmobile.data.database.entity.BotToolBinding
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.util.requiresLocalNetworkAccess
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.botPreferences by preferencesDataStore(name = "bot_preferences")

interface NewChatBotHint {
    suspend fun preferredPlatformUid(): String?

    object None : NewChatBotHint {
        override suspend fun preferredPlatformUid(): String? = null
    }
}

@Singleton
class BotRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val botDao: BotDao,
    private val toolConnectionDao: ToolConnectionDao
) : NewChatBotHint {
    suspend fun listBots(): List<Bot> = botDao.listBots()

    suspend fun getBot(botUid: String): Bot? = botDao.getBot(botUid)

    suspend fun listBindings(botUid: String): List<BotToolBinding> = botDao.listBindings(botUid)

    suspend fun listBindingsWithConnections(botUid: String): List<AgentToolBindingWithConnection> {
        val bindings = botDao.listBindings(botUid)
        val connections = bindings.mapNotNull { it.connectionUid }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { toolConnectionDao.getConnectionsByUids(it) }
            ?.associateBy { it.connectionUid }
            .orEmpty()
        return bindings.map { binding ->
            AgentToolBindingWithConnection(
                binding = binding.toAgentToolBinding(),
                connection = binding.connectionUid?.let { connections[it] }
            )
        }
    }

    suspend fun needsLocalNetwork(botUid: String): Boolean = listBindingsWithConnections(botUid).any { binding ->
        binding.connection?.endpointUrl?.let(::requiresLocalNetworkAccess) == true
    }

    suspend fun save(bot: Bot, bindings: List<BotToolBinding>) {
        val cleanBot = bot.copy(name = bot.name.trim(), instructions = bot.instructions.trim())
        require(cleanBot.name.isNotBlank()) { "A bot needs a name." }
        validateBindings(bindings)
        botDao.save(cleanBot, bindings)
    }

    suspend fun duplicate(botUid: String): Bot? {
        val source = botDao.getBot(botUid) ?: return null
        val now = System.currentTimeMillis() / 1000
        val copy = duplicatedBot(source, UUID.randomUUID().toString(), now)
        val bindings = botDao.listBindings(botUid).map { binding ->
            binding.copy(
                bindingUid = botBindingUid(copy.botUid, binding.connectionUid, binding.toolName),
                botUid = copy.botUid,
                createdAt = now
            )
        }
        botDao.save(copy, bindings)
        return copy
    }

    suspend fun delete(botUid: String) {
        botDao.clearChatBot(botUid)
        botDao.deleteBot(botUid)
        val stored = context.botPreferences.data.first()[LAST_BOT_UID]
        if (stored == botUid) {
            setLastSelectedBotUid(null)
        }
    }

    suspend fun resetBuiltIn(botUid: String): Bot? {
        val definition = BuiltInBots.definition(botUid) ?: return null
        botDao.save(definition.bot, definition.bindings)
        return definition.bot
    }

    suspend fun missingBuiltIns(): List<BuiltInBots.Definition> {
        val existing = botDao.listBots().map { it.botUid }.toSet()
        return BuiltInBots.missing(existing)
    }

    suspend fun restoreMissingBuiltIns() {
        missingBuiltIns().forEach { definition ->
            botDao.save(definition.bot, definition.bindings)
        }
    }

    suspend fun assignChatBot(chatId: Int, botUid: String?) {
        if (chatId <= 0) return
        botDao.updateChatBot(chatId, botUid)
    }

    suspend fun lastSelectedBotUid(): String? {
        val stored = context.botPreferences.data.first()[LAST_BOT_UID]
        if (stored != null) {
            if (stored.isBlank()) return null
            return botDao.getBot(stored)?.botUid
        }
        return botDao.getBot(BuiltInBots.ASSISTANT_UID)?.botUid
    }

    suspend fun setLastSelectedBotUid(botUid: String?) {
        context.botPreferences.edit { preferences ->
            preferences[LAST_BOT_UID] = botUid.orEmpty()
        }
    }

    override suspend fun preferredPlatformUid(): String? {
        val botUid = lastSelectedBotUid() ?: return null
        return botDao.getBot(botUid)?.defaultPlatformUid
    }

    private suspend fun validateBindings(bindings: List<BotToolBinding>) {
        val connectionUids = bindings.mapNotNull { it.connectionUid }.distinct()
        val connections = if (connectionUids.isEmpty()) {
            emptyMap()
        } else {
            toolConnectionDao.getConnectionsByUids(connectionUids).associateBy { it.connectionUid }
        }
        bindings.forEach { binding ->
            require(binding.toolName.isNotBlank()) { "Bot tool name is required." }
            when (binding.toolName) {
                BuiltInAgentTool.CURRENT_DATE,
                BuiltInAgentTool.READ_URL,
                BuiltInAgentTool.RUN_JAVASCRIPT -> require(binding.connectionUid == null) {
                    "Built-in bot tools do not use a connection."
                }

                BotToolNames.WEB_SEARCH -> {
                    val connection = connections[binding.connectionUid]
                    require(connection?.type in WEB_SEARCH_TYPES) { "Web search needs a search connection." }
                }

                BotToolNames.MCP_ALL -> {
                    val connection = connections[binding.connectionUid]
                    require(connection?.type == ToolConnectionType.MCP) { "MCP tools need an MCP server." }
                }

                else -> throw IllegalArgumentException("Unsupported bot tool.")
            }
        }
    }

    private companion object {
        val LAST_BOT_UID = stringPreferencesKey("last_bot_uid")
        val WEB_SEARCH_TYPES = setOf(
            ToolConnectionType.FIRECRAWL,
            ToolConnectionType.PERPLEXITY,
            ToolConnectionType.EXA
        )
    }
}
