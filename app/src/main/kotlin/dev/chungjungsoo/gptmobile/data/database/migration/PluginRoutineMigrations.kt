package dev.chungjungsoo.gptmobile.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Stream (k) schema. Assigned Room version is 20.
 * This branch starts at version 11, so the registered path is 11 → 20 and only
 * creates plugin and routine tables. Retarget the start version to 19 when
 * versions 12–19 are already on main.
 */
object PluginRoutineMigrations {
    val MIGRATION_11_20 = object : Migration(11, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `plugins` (
                    `plugin_uid` TEXT NOT NULL,
                    `catalog_id` TEXT,
                    `name` TEXT NOT NULL,
                    `endpoint_url` TEXT NOT NULL,
                    `transport` TEXT NOT NULL,
                    `auth_type` TEXT NOT NULL,
                    `secret_ref` TEXT,
                    `oauth_client_id` TEXT,
                    `allow_cleartext` INTEGER NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `status_detail` TEXT,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`plugin_uid`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `plugin_tools` (
                    `tool_uid` TEXT NOT NULL,
                    `plugin_uid` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `model_name` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `input_schema_json` TEXT NOT NULL,
                    `effect` TEXT NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    PRIMARY KEY(`tool_uid`),
                    FOREIGN KEY(`plugin_uid`) REFERENCES `plugins`(`plugin_uid`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_plugin_tools_plugin_uid` ON `plugin_tools` (`plugin_uid`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_plugin_tools_plugin_uid_name` ON `plugin_tools` (`plugin_uid`, `name`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `plugin_scope_selections` (
                    `selection_uid` TEXT NOT NULL,
                    `scope_type` TEXT NOT NULL,
                    `scope_id` TEXT NOT NULL,
                    `plugin_uid` TEXT NOT NULL,
                    PRIMARY KEY(`selection_uid`),
                    FOREIGN KEY(`plugin_uid`) REFERENCES `plugins`(`plugin_uid`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_plugin_scope_selections_plugin_uid` ON `plugin_scope_selections` (`plugin_uid`)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_plugin_scope_selections_scope_type_scope_id_plugin_uid` ON `plugin_scope_selections` (`scope_type`, `scope_id`, `plugin_uid`)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `routines` (
                    `routine_uid` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `prompt` TEXT NOT NULL,
                    `bot_uid` TEXT,
                    `platform_uid` TEXT NOT NULL,
                    `schedule_kind` TEXT NOT NULL,
                    `cron_expression` TEXT,
                    `hour` INTEGER NOT NULL,
                    `minute` INTEGER NOT NULL,
                    `day_of_week` INTEGER NOT NULL,
                    `plugin_uids` TEXT NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    `chat_id` INTEGER,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`routine_uid`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `routine_runs` (
                    `run_uid` TEXT NOT NULL,
                    `routine_uid` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `result_text` TEXT,
                    `error_text` TEXT,
                    `chat_id` INTEGER,
                    `started_at` INTEGER NOT NULL,
                    `finished_at` INTEGER,
                    PRIMARY KEY(`run_uid`),
                    FOREIGN KEY(`routine_uid`) REFERENCES `routines`(`routine_uid`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_runs_routine_uid` ON `routine_runs` (`routine_uid`)")
        }
    }

    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE routines ADD COLUMN allowed_read_tools TEXT NOT NULL DEFAULT ''")
        }
    }
}
