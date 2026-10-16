package dev.chungjungsoo.gptmobile.data.image

import android.content.Context
import java.io.File

fun interface GeneratedImageDirectoryProvider {
    fun directory(): File
}

class ContextGeneratedImageDirectoryProvider(
    private val context: Context
) : GeneratedImageDirectoryProvider {
    override fun directory(): File = File(context.filesDir, GENERATED_IMAGE_DIRECTORY)
}

const val GENERATED_IMAGE_DIRECTORY = "generated-images"
