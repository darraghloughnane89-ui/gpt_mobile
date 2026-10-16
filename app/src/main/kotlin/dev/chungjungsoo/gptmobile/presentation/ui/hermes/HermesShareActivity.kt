package dev.chungjungsoo.gptmobile.presentation.ui.hermes

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.hermes.HermesAttachment
import dev.chungjungsoo.gptmobile.data.hermes.HermesHandoff
import dev.chungjungsoo.gptmobile.data.hermes.HermesHandoffKind
import dev.chungjungsoo.gptmobile.data.hermes.HermesHandoffRequest
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class HermesShareActivity : ComponentActivity() {
    @Inject
    lateinit var handoff: HermesHandoff

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var error by mutableStateOf<String?>(null)
        lifecycleScope.launch {
            val request = withContext(Dispatchers.IO) { readShare(intent) }
            if (request == null) {
                error = getString(R.string.hermes_share_empty)
            } else {
                handoff.submit(request)
            }
        }
        setContent {
            GPTMobileTheme {
                if (error != null) {
                    Text(error.orEmpty())
                } else {
                    HermesThreadRoute(onBack = { finish() })
                }
            }
        }
    }

    private fun readShare(intent: Intent): HermesHandoffRequest? {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val files = shareUris(intent).mapNotNull { uri -> copySharedUri(uri) }
        return shareRequest(text, subject, files)
    }

    private fun shareUris(intent: Intent): List<Uri> {
        val single = streamUri(intent)
        val multiple = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        return buildList {
            if (single != null) add(single)
            multiple?.let { addAll(it) }
        }.distinct()
    }

    private fun streamUri(intent: Intent): Uri? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }

    private fun copySharedUri(uri: Uri): HermesAttachment? {
        val name = displayName(uri)
        val directory = File(noBackupFilesDir, "hermes-inbox")
        directory.mkdirs()
        val target = File(directory, "${System.currentTimeMillis()}_$name")
        val copied = try {
            contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        } catch (_: Exception) {
            false
        }
        if (!copied) return null
        return HermesAttachment(
            displayName = name,
            path = target.absolutePath,
            mimeType = contentResolver.getType(uri).orEmpty()
        )
    }

    private fun displayName(uri: Uri): String {
        var name = "shared-file"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index) ?: name
            }
        }
        return name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "shared-file" }
    }
}

internal fun shareRequest(
    text: String?,
    subject: String?,
    files: List<HermesAttachment>
): HermesHandoffRequest? {
    val body = listOfNotNull(subject?.trim()?.takeIf { it.isNotEmpty() }, text?.trim()?.takeIf { it.isNotEmpty() })
        .joinToString("\n\n")
    if (body.isBlank() && files.isEmpty()) return null
    return HermesHandoffRequest(
        kind = if (files.isEmpty()) HermesHandoffKind.MESSAGE else HermesHandoffKind.FILE,
        text = body,
        attachments = files
    )
}

@Composable
private fun HermesThreadRoute(onBack: () -> Unit) {
    val viewModel: HermesViewModel = hiltViewModel()
    val state by viewModel.threadUi.collectAsStateWithLifecycle()
    HermesThreadScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onNewThread = viewModel::newThread
    )
}
