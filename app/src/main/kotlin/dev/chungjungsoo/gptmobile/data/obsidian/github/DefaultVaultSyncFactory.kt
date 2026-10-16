package dev.chungjungsoo.gptmobile.data.obsidian.github

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.obsidian.ObsidianSyncPreferences
import dev.chungjungsoo.gptmobile.data.obsidian.VaultBackend
import dev.chungjungsoo.gptmobile.data.obsidian.VaultCommit
import dev.chungjungsoo.gptmobile.data.obsidian.VaultCommitResult
import dev.chungjungsoo.gptmobile.data.obsidian.VaultPaths
import dev.chungjungsoo.gptmobile.data.obsidian.VaultReadResult
import dev.chungjungsoo.gptmobile.data.obsidian.VaultSync
import dev.chungjungsoo.gptmobile.data.obsidian.VaultSyncFactory
import dev.chungjungsoo.gptmobile.data.obsidian.local.LocalFolderVaultSync
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Singleton
class DefaultVaultSyncFactory @Inject constructor(
    private val preferences: ObsidianSyncPreferences,
    private val tokenStore: ObsidianGitHubTokenStore,
    @ApplicationContext private val context: Context
) : VaultSyncFactory {
    private val http = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun open(): VaultSync {
        val config = preferences.load()
        val root = VaultPaths.normalizeRoot(config.rootFolder)
        return when (config.vaultBackend) {
            VaultBackend.GITHUB -> {
                val token = tokenStore.read().orEmpty()
                if (token.isBlank()) {
                    UnavailableVaultSync(
                        "Add a GitHub token for ${config.githubOwner}/${config.githubRepo}. " +
                            "The token stays in the Keystore vault."
                    )
                } else {
                    GitHubVaultSync(
                        http = http,
                        apiBase = "https://api.github.com",
                        token = token,
                        owner = config.githubOwner,
                        repo = config.githubRepo,
                        branch = config.githubBranch.ifBlank { "main" },
                        rootFolder = root
                    )
                }
            }
            VaultBackend.LOCAL -> {
                val uri = config.localTreeUri
                if (uri.isNullOrBlank()) {
                    UnavailableVaultSync("Choose a folder on this phone, or switch back to GitHub.")
                } else {
                    LocalFolderVaultSync(context.contentResolver, Uri.parse(uri), root)
                }
            }
        }
    }
}

class UnavailableVaultSync(
    private val message: String
) : VaultSync {
    override suspend fun readTree(): VaultReadResult = VaultReadResult.Failed(message)

    override suspend fun commit(change: VaultCommit): VaultCommitResult = VaultCommitResult.Failed(message)
}
