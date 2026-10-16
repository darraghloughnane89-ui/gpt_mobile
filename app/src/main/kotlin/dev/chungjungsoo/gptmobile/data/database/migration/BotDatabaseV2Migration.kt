package dev.chungjungsoo.gptmobile.data.database.migration

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.chungjungsoo.gptmobile.data.bot.BuiltInBots

object BotDatabaseV2Migration {
    const val CREATE_BOTS_SQL = "CREATE TABLE IF NOT EXISTS `bots` (" +
        "`bot_uid` TEXT NOT NULL, " +
        "`name` TEXT NOT NULL, " +
        "`avatar_emoji` TEXT, " +
        "`avatar_color` TEXT, " +
        "`instructions` TEXT NOT NULL, " +
        "`default_platform_uid` TEXT, " +
        "`default_model` TEXT, " +
        "`starter_prompts` TEXT NOT NULL, " +
        "`builtin` INTEGER NOT NULL, " +
        "`created_at` INTEGER NOT NULL, " +
        "`updated_at` INTEGER NOT NULL, " +
        "PRIMARY KEY(`bot_uid`))"

    const val CREATE_BOT_TOOL_BINDINGS_SQL = "CREATE TABLE IF NOT EXISTS `bot_tool_bindings` (" +
        "`binding_uid` TEXT NOT NULL, " +
        "`bot_uid` TEXT NOT NULL, " +
        "`connection_uid` TEXT, " +
        "`tool_name` TEXT NOT NULL, " +
        "`created_at` INTEGER NOT NULL, " +
        "PRIMARY KEY(`binding_uid`), " +
        "FOREIGN KEY(`bot_uid`) REFERENCES `bots`(`bot_uid`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
        "FOREIGN KEY(`connection_uid`) REFERENCES `tool_connections`(`connection_uid`) ON UPDATE NO ACTION ON DELETE CASCADE )"

    val CREATE_BOT_INDEX_SQL = listOf(
        "CREATE INDEX IF NOT EXISTS `index_bot_tool_bindings_bot_uid` ON `bot_tool_bindings` (`bot_uid`)",
        "CREATE INDEX IF NOT EXISTS `index_bot_tool_bindings_connection_uid` ON `bot_tool_bindings` (`connection_uid`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_bot_tool_bindings_bot_uid_connection_uid_tool_name` " +
            "ON `bot_tool_bindings` (`bot_uid`, `connection_uid`, `tool_name`)"
    )

    const val ADD_CHAT_BOT_UID_SQL = "ALTER TABLE `chats_v2` ADD COLUMN `bot_uid` TEXT"

    const val CREATE_CHAT_BOT_INDEX_SQL = "CREATE INDEX IF NOT EXISTS `index_chats_v2_bot_uid` ON `chats_v2` (`bot_uid`)"

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_BOTS_SQL)
            db.execSQL(CREATE_BOT_TOOL_BINDINGS_SQL)
            CREATE_BOT_INDEX_SQL.forEach(db::execSQL)
            db.execSQL(ADD_CHAT_BOT_UID_SQL)
            db.execSQL(CREATE_CHAT_BOT_INDEX_SQL)
            seedBuiltInBots(db)
        }
    }

    val SEED_CALLBACK = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            seedBuiltInBots(db)
        }
    }

    fun seedBuiltInBots(db: SupportSQLiteDatabase) {
        BuiltInBots.definitions().forEach { definition ->
            val bot = definition.bot
            db.execSQL(
                "INSERT OR IGNORE INTO bots (" +
                    "bot_uid, name, avatar_emoji, avatar_color, instructions, " +
                    "default_platform_uid, default_model, starter_prompts, builtin, created_at, updated_at" +
                    ") VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    bot.botUid,
                    bot.name,
                    bot.avatarEmoji,
                    bot.avatarColor,
                    bot.instructions,
                    bot.starterPrompts,
                    if (bot.builtin) 1 else 0,
                    bot.createdAt,
                    bot.updatedAt
                )
            )
            definition.bindings.forEach { binding ->
                db.execSQL(
                    "INSERT OR IGNORE INTO bot_tool_bindings (" +
                        "binding_uid, bot_uid, connection_uid, tool_name, created_at" +
                        ") VALUES (?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        binding.bindingUid,
                        binding.botUid,
                        binding.connectionUid,
                        binding.toolName,
                        binding.createdAt
                    )
                )
            }
        }
    }
}
