package dev.chungjungsoo.gptmobile.data.hermes

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class AppHermesAttachmentRoots @Inject constructor(
    @param:ApplicationContext private val context: Context
) : HermesAttachmentRoots {
    override fun roots(): List<File> = listOf(
        File(context.filesDir, "attachments"),
        File(context.noBackupFilesDir, "hermes-inbox")
    )
}
