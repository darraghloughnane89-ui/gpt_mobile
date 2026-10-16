package dev.chungjungsoo.gptmobile.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object PhoneControlMigrations {
    val MIGRATION_11_21 = object : Migration(11, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `phone_allowlist` (
                    `package_name` TEXT NOT NULL,
                    `label` TEXT NOT NULL,
                    `allowed` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`package_name`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `phone_action_log` (
                    `id` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `package_name` TEXT NOT NULL,
                    `action` TEXT NOT NULL,
                    `summary` TEXT NOT NULL,
                    `outcome` TEXT NOT NULL,
                    `confirmed` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `phone_routines` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `approved` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `robin_vault_meta` (
                    `id` TEXT NOT NULL,
                    `secret_ref` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `username` TEXT,
                    `package_name` TEXT,
                    `cert_sha256` TEXT,
                    `web_domain` TEXT,
                    `created_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }
    }
}
