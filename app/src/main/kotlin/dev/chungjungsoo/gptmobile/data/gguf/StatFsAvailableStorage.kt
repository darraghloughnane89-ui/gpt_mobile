package dev.chungjungsoo.gptmobile.data.gguf

import android.os.StatFs
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatFsAvailableStorage @Inject constructor() : AvailableStorage {
    override fun availableBytes(directory: File): Long {
        val target = if (directory.isDirectory) directory else directory.parentFile?.takeIf { it.isDirectory } ?: return 0L
        return runCatching { StatFs(target.absolutePath).availableBytes }.getOrDefault(0L)
    }
}
