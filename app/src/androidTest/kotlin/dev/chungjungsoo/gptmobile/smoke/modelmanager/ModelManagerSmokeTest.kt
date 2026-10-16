package dev.chungjungsoo.gptmobile.smoke.modelmanager

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chungjungsoo.gptmobile.smoke.FeatureSmokeHarness
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Offline GGUF model manager checklist. The pasted URL is not a Hugging Face link, so nothing is downloaded.
 * Skips until [dev.chungjungsoo.gptmobile.presentation.ui.gguf.GgufModelsViewModel] is in the APK.
 */
@RunWith(AndroidJUnit4::class)
@SmokeFeature("model-manager")
class ModelManagerSmokeTest :
    FeatureSmokeHarness(
        markerClass = "dev.chungjungsoo.gptmobile.presentation.ui.gguf.GgufModelsViewModel",
        feature = "model-manager"
    ) {
    @Test
    fun suggestedCatalogIsVisibleAndOtherHostsAreRejected() {
        launchMain()
        openSettings()
        clickText(featureText("gguf_models"))
        waitForText(featureText("gguf_empty_title"))
        waitForText(featureText("gguf_suggested_models"))
        waitForText("Qwen", substring = true)
        waitForText("Llama", substring = true)
        waitForText("Gemma", substring = true)
        composeRule.onNodeWithTag("gguf_hf_url").performTextInput("https://example.com/model.bin")
        clickText(featureText("gguf_download"))
        waitForText("Paste a Hugging Face link to a .gguf file.")
    }
}
