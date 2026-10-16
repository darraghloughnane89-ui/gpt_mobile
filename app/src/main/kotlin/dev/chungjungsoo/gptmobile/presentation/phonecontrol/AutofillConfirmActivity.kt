package dev.chungjungsoo.gptmobile.presentation.phonecontrol

import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.phonecontrol.PackageSignature
import dev.chungjungsoo.gptmobile.data.phonecontrol.vault.AutofillIntentPolicy
import dev.chungjungsoo.gptmobile.data.phonecontrol.vault.AutofillTrigger
import dev.chungjungsoo.gptmobile.data.phonecontrol.vault.RobinVaultStore
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AutofillConfirmActivity : ComponentActivity() {
    @Inject
    lateinit var store: RobinVaultStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = android.hardware.biometrics.BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.phone_control_biometric_title))
            .setDescription(getString(R.string.phone_control_biometric_fill))
            .setNegativeButton(getString(android.R.string.cancel), executor) { _, _ -> finish() }
            .build()
        prompt.authenticate(
            CancellationSignal(),
            executor,
            object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult) {
                    fill()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Toast.makeText(this@AutofillConfirmActivity, R.string.phone_control_autofill_blocked, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        )
    }

    private fun fill() {
        val passwordId = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_PASSWORD_ID, AutofillId::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PASSWORD_ID)
        }
        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val domain = intent.getStringExtra(EXTRA_DOMAIN)
        if (passwordId == null || !AutofillIntentPolicy.allow(AutofillTrigger.AUTOFILL_UI_TAP, true)) {
            finish()
            return
        }
        lifecycleScope.launch {
            val cert = PackageSignature.certSha256(this@AutofillConfirmActivity, packageName).orEmpty()
            val secret = runCatching { store.readFirstMatch(packageName, cert, domain) }.getOrNull()
            if (secret == null) {
                Toast.makeText(this@AutofillConfirmActivity, R.string.phone_control_fill_failed, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            val text = secret.decodeToString()
            secret.fill(0)
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1)
            presentation.setTextViewText(android.R.id.text1, getString(R.string.phone_control_autofill_presentation))
            val dataset = Dataset.Builder(presentation)
                .setValue(passwordId, AutofillValue.forText(text))
                .build()
            val response = FillResponse.Builder().addDataset(dataset).build()
            setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response))
            finish()
        }
    }

    companion object {
        const val EXTRA_PASSWORD_ID = "password_id"
        const val EXTRA_USERNAME_ID = "username_id"
        const val EXTRA_PACKAGE = "package_name"
        const val EXTRA_DOMAIN = "web_domain"
    }
}
