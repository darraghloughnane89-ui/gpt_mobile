package dev.chungjungsoo.gptmobile.data.database.migration

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.chungjungsoo.gptmobile.data.usage.ModelPriceCatalog

object UsageMigration {
    val CREATE_USAGE_EVENTS = """
        CREATE TABLE IF NOT EXISTS `usage_events` (
            `usage_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `platform_uid` TEXT NOT NULL,
            `model` TEXT NOT NULL,
            `chat_id` INTEGER,
            `input_tokens` INTEGER NOT NULL,
            `output_tokens` INTEGER NOT NULL,
            `estimated` INTEGER NOT NULL,
            `cost_micros` INTEGER NOT NULL,
            `created_at` INTEGER NOT NULL
        )
    """.trimIndent()

    val CREATE_MODEL_PRICES = """
        CREATE TABLE IF NOT EXISTS `model_prices` (
            `model_key` TEXT NOT NULL,
            `input_micros_per_million` INTEGER NOT NULL,
            `output_micros_per_million` INTEGER NOT NULL,
            `updated_at` INTEGER NOT NULL,
            PRIMARY KEY(`model_key`)
        )
    """.trimIndent()

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_USAGE_EVENTS)
            db.execSQL(CREATE_MODEL_PRICES)
            ModelPriceCatalog.insertStatements().forEach(db::execSQL)
        }
    }

    val SEED_PRICES_CALLBACK = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            ModelPriceCatalog.insertStatements().forEach(db::execSQL)
        }
    }
}
