package dev.chungjungsoo.gptmobile.smoke.attachments

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.Intent
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.smoke.FakeChatServer
import dev.chungjungsoo.gptmobile.smoke.FeatureSmokeHarness
import dev.chungjungsoo.gptmobile.smoke.SMOKE_REPLY
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import java.io.File
import java.io.RandomAccessFile
import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * In-chat attachment checklist: documents, vision, image save/share, and no new camera button.
 * Skips until [dev.chungjungsoo.gptmobile.data.attachment.DocumentTextExtractor] is in the APK.
 */
@RunWith(AndroidJUnit4::class)
@SmokeFeature("attachments")
class AttachmentsSmokeTest :
    FeatureSmokeHarness(
        markerClass = "dev.chungjungsoo.gptmobile.data.attachment.DocumentTextExtractor",
        feature = "attachments"
    ) {
    @Test
    fun documentChipCanBeRemovedAndSentWithoutTypedText() {
        val note = writeFile("smoke-note.txt", "hello from the attachment".toByteArray())
        Intents.init()
        try {
            stubDocumentPicker(note)
            launchMain()
            openNewChat()
            assertFalse(hasContentDescription(context.getString(R.string.take_photo)))
            clickContentDescription(context.getString(R.string.attach_file))
            waitForText("smoke-note.txt")
            clickContentDescription(featureText("remove_attachment", "smoke-note.txt"))
            waitUntilGone("smoke-note.txt")
            stubDocumentPicker(note)
            clickContentDescription(context.getString(R.string.attach_file))
            waitForText(featureText("attachment_document_included"))
            clickContentDescription(context.getString(R.string.send))
            waitForText(SMOKE_REPLY)
            goBack()
            waitForHome()
            val documentTitle = "hello from the attachment"
            val untitled = context.getString(R.string.untitled_chat)
            composeRule.waitUntil(UI_TIMEOUT_MS) {
                hasText(documentTitle) || hasText(untitled)
            }
            clickText(if (hasText(documentTitle)) documentTitle else untitled)
            waitForText(SMOKE_REPLY)
        } finally {
            Intents.release()
        }
    }

    @Test
    fun oversizedDocumentShowsTheSizeError() {
        val huge = writeFile("smoke-big.pdf", ByteArray(0))
        RandomAccessFile(huge, "rw").use { file -> file.setLength(21L * 1024 * 1024) }
        Intents.init()
        try {
            stubDocumentPicker(huge)
            launchMain()
            openNewChat()
            clickContentDescription(context.getString(R.string.attach_file))
            waitForText(featureText("attachment_document_too_large"))
        } finally {
            Intents.release()
        }
    }

    @Test
    fun longDocumentShowsTheTruncationNotice() {
        val longNote = writeFile("smoke-long.txt", "a".repeat(90_000).toByteArray())
        Intents.init()
        try {
            stubDocumentPicker(longNote)
            launchMain()
            openNewChat()
            clickContentDescription(context.getString(R.string.attach_file))
            waitForText(featureText("attachment_document_truncated"))
        } finally {
            Intents.release()
        }
    }

    @Test
    fun imageIsIncludedAsVisionInput() {
        val png = writeFile("smoke.png", Base64.getDecoder().decode(TINY_PNG))
        Intents.init()
        try {
            stubDocumentPicker(png)
            launchMain()
            openNewChat()
            clickContentDescription(context.getString(R.string.attach_file))
            waitForText("smoke.png")
            typeAndSend("What is this?")
            waitForText(SMOKE_REPLY)
            val sawImage = server.requests.any { request ->
                request.path.contains("chat/completions") &&
                    (request.body.contains("image") || request.body.contains("iVBOR"))
            }
            assertTrue(sawImage)
            assertFalse(server.requests.any { it.path.contains("images/generations") })
        } finally {
            Intents.release()
        }
    }

    @Test
    fun generatedImageCanBeSavedAndShared() {
        useModel(FAKE_PLATFORM, "gpt-image-1")
        server.script = FakeChatServer.Script.IMAGE
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            )
            launchMain()
            openNewChat()
            typeAndSend("Draw a red dot")
            waitForContent(featureText("save_image"))
            clickContentDescription(featureText("save_image"))
            composeRule.waitUntil(UI_TIMEOUT_MS) { savedToPictures() }
            clickContentDescription(featureText("share_image"))
            Intents.intended(hasAction(Intent.ACTION_CHOOSER))
        } finally {
            Intents.release()
        }
    }

    private fun stubDocumentPicker(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val result = Intent().apply {
            data = uri
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, result)
        )
    }

    private fun writeFile(name: String, bytes: ByteArray): File {
        val directory = File(context.filesDir, "attachments")
        directory.mkdirs()
        return File(directory, name).apply { writeBytes(bytes) }
    }

    private fun waitForContent(description: String) {
        composeRule.waitUntil(UI_TIMEOUT_MS) { hasContentDescription(description) }
    }

    private fun savedToPictures(): Boolean {
        val resolver = context.contentResolver
        val cursor = resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
            null,
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        ) ?: return false
        cursor.use {
            while (it.moveToNext()) {
                val path = it.getString(0).orEmpty()
                if (path.contains("GPTMobile")) return true
            }
        }
        return false
    }

    private companion object {
        const val TINY_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    }
}
