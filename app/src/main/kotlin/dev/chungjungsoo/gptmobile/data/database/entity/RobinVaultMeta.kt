package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "robin_vault_meta")
data class RobinVaultMeta(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "secret_ref")
    val secretRef: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "username")
    val username: String?,

    @ColumnInfo(name = "package_name")
    val packageName: String?,

    @ColumnInfo(name = "cert_sha256")
    val certSha256: String?,

    @ColumnInfo(name = "web_domain")
    val webDomain: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
