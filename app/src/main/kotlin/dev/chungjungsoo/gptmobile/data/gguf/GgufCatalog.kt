package dev.chungjungsoo.gptmobile.data.gguf

/**
 * Small chat GGUFs that fit a phone in the Pixel 9 Pro class (16 GB RAM).
 *
 * Checked against Hugging Face on 2026-10-16. Each file returned HTTP 200 from its
 * resolve URL with a Content-Length equal to [GgufCatalogEntry.sizeBytes]. The SHA-256
 * is the Git LFS oid published in the pointer (`oid sha256:…`), which is not the HTTP ETag.
 * Licenses were read from the model card and the LICENSE file in the same repo.
 */
object GgufCatalog {
    val entries: List<GgufCatalogEntry> = listOf(
        GgufCatalogEntry(
            id = "qwen2.5-1.5b-instruct-q4km",
            displayName = "Qwen2.5 1.5B Instruct",
            family = "Qwen",
            parameterLabel = "1.5B",
            quantization = "Q4_K_M",
            repoId = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sizeBytes = 1_117_320_736L,
            sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
            licenseName = "Apache-2.0",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            sourceNote = "Official Qwen GGUF"
        ),
        GgufCatalogEntry(
            id = "qwen2.5-3b-instruct-q4km",
            displayName = "Qwen2.5 3B Instruct",
            family = "Qwen",
            parameterLabel = "3B",
            quantization = "Q4_K_M",
            repoId = "Qwen/Qwen2.5-3B-Instruct-GGUF",
            fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
            sizeBytes = 2_104_932_768L,
            sha256 = "626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d",
            licenseName = "Qwen Research License",
            licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/blob/main/LICENSE",
            sourceNote = "Official Qwen GGUF. This size is under the Qwen Research License, not Apache-2.0."
        ),
        GgufCatalogEntry(
            id = "qwen3-4b-q4km",
            displayName = "Qwen3 4B",
            family = "Qwen",
            parameterLabel = "4B",
            quantization = "Q4_K_M",
            repoId = "Qwen/Qwen3-4B-GGUF",
            fileName = "Qwen3-4B-Q4_K_M.gguf",
            sizeBytes = 2_497_280_256L,
            sha256 = "7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5",
            licenseName = "Apache-2.0",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            sourceNote = "Official Qwen3 chat GGUF"
        ),
        GgufCatalogEntry(
            id = "llama-3.2-1b-instruct-q4km",
            displayName = "Llama 3.2 1B Instruct",
            family = "Llama",
            parameterLabel = "1B",
            quantization = "Q4_K_M",
            repoId = "bartowski/Llama-3.2-1B-Instruct-GGUF",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sizeBytes = 807_694_464L,
            sha256 = "6f85a640a97cf2bf5b8e764087b1e83da0fdb51d7c9fab7d0fece9385611df83",
            licenseName = "Llama 3.2 Community License",
            licenseUrl = "https://github.com/meta-llama/llama-models/blob/main/models/llama3_2/LICENSE",
            sourceNote = "bartowski quant of meta-llama/Llama-3.2-1B-Instruct"
        ),
        GgufCatalogEntry(
            id = "llama-3.2-3b-instruct-q4km",
            displayName = "Llama 3.2 3B Instruct",
            family = "Llama",
            parameterLabel = "3B",
            quantization = "Q4_K_M",
            repoId = "bartowski/Llama-3.2-3B-Instruct-GGUF",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            sizeBytes = 2_019_377_696L,
            sha256 = "6c1a2b41161032677be168d354123594c0e6e67d2b9227c84f296ad037c728ff",
            licenseName = "Llama 3.2 Community License",
            licenseUrl = "https://github.com/meta-llama/llama-models/blob/main/models/llama3_2/LICENSE",
            sourceNote = "bartowski quant of meta-llama/Llama-3.2-3B-Instruct"
        ),
        GgufCatalogEntry(
            id = "gemma-3-1b-it-q4km",
            displayName = "Gemma 3 1B Instruct",
            family = "Gemma",
            parameterLabel = "1B",
            quantization = "Q4_K_M",
            repoId = "unsloth/gemma-3-1b-it-GGUF",
            fileName = "gemma-3-1b-it-Q4_K_M.gguf",
            sizeBytes = 806_058_272L,
            sha256 = "8270790f3ab69fdfe860b7b64008d9a19986d8df7e407bb018184caa08798ebd",
            licenseName = "Gemma Terms of Use",
            licenseUrl = "https://ai.google.dev/gemma/terms",
            sourceNote = "Unsloth quant of google/gemma-3-1b-it"
        ),
        GgufCatalogEntry(
            id = "gemma-3-4b-it-q4km",
            displayName = "Gemma 3 4B Instruct",
            family = "Gemma",
            parameterLabel = "4B",
            quantization = "Q4_K_M",
            repoId = "unsloth/gemma-3-4b-it-GGUF",
            fileName = "gemma-3-4b-it-Q4_K_M.gguf",
            sizeBytes = 2_489_894_016L,
            sha256 = "04a43a22e8d2003deda5acc262f68ec1005fa76c735a9962a8c77042a74a7d19",
            licenseName = "Gemma Terms of Use",
            licenseUrl = "https://ai.google.dev/gemma/terms",
            sourceNote = "Unsloth quant of google/gemma-3-4b-it"
        )
    )

    fun find(id: String): GgufCatalogEntry? = entries.firstOrNull { it.id == id }
}

data class GgufCatalogEntry(
    val id: String,
    val displayName: String,
    val family: String,
    val parameterLabel: String,
    val quantization: String,
    val repoId: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val licenseName: String,
    val licenseUrl: String,
    val sourceNote: String
) {
    val downloadUrl: String = "https://huggingface.co/$repoId/resolve/main/$fileName"

    fun toDownloadRequest(): GgufDownloadRequest = GgufDownloadRequest(
        downloadKey = id,
        displayName = displayName,
        url = downloadUrl,
        fileName = fileName,
        sizeBytes = sizeBytes,
        sha256 = sha256,
        licenseName = licenseName,
        catalogId = id,
        sourceLabel = downloadUrl
    )
}
