package dev.chungjungsoo.gptmobile.util

/**
 * Product name used by new surfaces. Vault paths default to [VAULT_ROOT]
 * so a later rename does not move notes that are already in the vault.
 */
object Brand {
    const val NAME = "Robin"
    const val ALT_NAME = "Rocco"
    const val VAULT_ROOT = "Robin"
    const val VAULT_GITHUB_OWNER = "darraghloughnane89-ui"
    const val VAULT_GITHUB_REPO = "knowledge-vault"
    const val VAULT_GITHUB_BRANCH = "main"

    val knownNames: List<String> = listOf(NAME, ALT_NAME)
}
