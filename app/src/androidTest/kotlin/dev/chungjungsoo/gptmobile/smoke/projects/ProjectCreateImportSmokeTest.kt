package dev.chungjungsoo.gptmobile.smoke.projects

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.core.content.FileProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.smoke.SmokeActivityTest
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@SmokeFeature("projects")
class ProjectCreateImportSmokeTest : SmokeActivityTest("projects") {
    @Test
    fun projectCreate_savesANamedProject() {
        val newProject = assumeFeature("project_new")
        val projects = assumeFeature("projects")
        launchSeededHome()
        waitForHome()
        clickText(projects)
        clickText(newProject)
        composeRule.onNode(hasSetTextAction()).performTextInput("Harness Project")
        clickText(targetContext.getString(R.string.save))
        val detailMarker = optionalString("project_no_instructions")
        if (detailMarker != null) {
            waitForText(detailMarker, timeoutMs = 20_000)
        }
        waitForText("Harness Project")
    }

    @Test
    fun projectImport_syntheticZip_showsImportedProject() {
        val importZip = assumeFeature("project_import_zip")
        val import = assumeFeature("project_import")
        val projects = assumeFeature("projects")
        launchSeededHome()
        waitForHome()
        clickText(projects)
        var intentsReady = false
        try {
            Intents.init()
            intentsReady = true
            val result = Intent()
                .setData(syntheticZipUri())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
                Instrumentation.ActivityResult(Activity.RESULT_OK, result)
            )
            clickText(import)
            clickText(importZip)
            composeRule.waitUntil(30_000) {
                hasText("Synthetic Import") || hasText("Imported", substring = true)
            }
        } finally {
            if (intentsReady) Intents.release()
        }
    }

    private fun syntheticZipUri(): Uri {
        val directory = checkNotNull(targetContext.getExternalFilesDir(null))
        val file = File(directory, "synthetic-project.zip")
        file.writeBytes(syntheticProjectZip())
        return FileProvider.getUriForFile(
            targetContext,
            "${targetContext.packageName}.fileprovider",
            file
        )
    }
}

private fun syntheticProjectZip(): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        fun put(path: String, text: String) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(text.toByteArray())
            zip.closeEntry()
        }
        put(
            "manifest.json",
            """{"format":"robin-project","name":"Synthetic Import","importKey":"smoke-synthetic"}"""
        )
        put("instructions.md", "Use the synthetic notes.")
        put(
            "chats/welcome.json",
            """{"id":"welcome","title":"Imported chat","messages":[{"role":"user","text":"Hello project"}]}"""
        )
        put("files/notes.md", "Synthetic note")
    }
    return output.toByteArray()
}
