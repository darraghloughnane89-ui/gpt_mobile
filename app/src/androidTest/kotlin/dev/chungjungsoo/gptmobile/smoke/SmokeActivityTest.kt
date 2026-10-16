package dev.chungjungsoo.gptmobile.smoke

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription as contentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import dagger.hilt.android.testing.HiltAndroidRule
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.dao.CompactionDao
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.ModelCapacityEntity
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description

const val SMOKE_REPLY = "SmokeReplyOk"
private const val HOME_TIMEOUT_MS = 45_000L
private const val UI_TIMEOUT_MS = 20_000L

/**
 * Shared launch, fake-provider, and gesture helpers for end-to-end smoke tests.
 * Subclasses opt into Hilt with [@dagger.hilt.android.testing.HiltAndroidTest].
 */
abstract class SmokeActivityTest(private val feature: String) {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createEmptyComposeRule()

    @get:Rule(order = 3)
    val diagnostics = SmokeDiagnosticsRule(feature)

    @Inject
    lateinit var platformDao: PlatformV2Dao

    @Inject
    lateinit var compactionDao: CompactionDao

    protected val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var llm: SmokeLlmServer? = null
    protected val requestedPaths: List<String>
        get() = llm?.requestedPaths.orEmpty()
    protected lateinit var scenario: ActivityScenario<out Activity>

    @Before
    fun injectHarness() {
        hiltRule.inject()
    }

    @After
    fun finishHarness() {
        SmokeCaptures.capture(feature)
        if (::scenario.isInitialized) {
            scenario.close()
        }
        llm?.close()
    }

    protected fun startFakeLlm() {
        llm?.close()
        llm = SmokeLlmServer()
    }

    protected fun seedFakeProvider() {
        val port = checkNotNull(llm) { "startFakeLlm() before seeding the provider" }.port
        runBlocking {
            platformDao.getPlatforms().forEach { platform -> platformDao.deletePlatform(platform) }
            val apiUrl = "http://127.0.0.1:$port/v1/"
            val id = platformDao.addPlatform(
                PlatformV2(
                    name = "Fake LLM",
                    compatibleType = ClientType.CUSTOM,
                    enabled = true,
                    apiUrl = apiUrl,
                    token = "smoke-token",
                    model = "fake-model",
                    stream = true,
                    timeout = 30
                )
            )
            val saved = checkNotNull(platformDao.getPlatform(id.toInt()))
            compactionDao.upsertCapacity(
                ModelCapacityEntity(
                    platformUid = saved.uid,
                    endpoint = saved.apiUrl,
                    model = saved.model,
                    detectedContextTokens = 128_000,
                    updatedAt = System.currentTimeMillis() / 1000
                )
            )
        }
    }

    protected fun launchSeededHome() {
        startFakeLlm()
        seedFakeProvider()
        launch(Intent(targetContext, MainActivity::class.java))
    }

    protected fun launch(intent: Intent) {
        scenario = ActivityScenario.launch(intent)
        composeRule.waitForIdle()
    }

    protected fun openNewChat() {
        val newChat = targetContext.getString(R.string.new_chat)
        val ask = targetContext.getString(R.string.ask_a_question)
        val selectPlatform = targetContext.getString(R.string.select_platform)
        val confirm = targetContext.getString(R.string.confirm)
        val cancel = targetContext.getString(R.string.cancel)
        repeat(3) {
            clickText(newChat, HOME_TIMEOUT_MS)
            composeRule.waitUntil(HOME_TIMEOUT_MS) {
                hasText(ask) || hasText(selectPlatform) || deviceHasText(ask) || deviceHasText(selectPlatform)
            }
            if (hasText(ask) || deviceHasText(ask)) return
            if (hasText("Fake LLM") || deviceHasText("Fake LLM")) {
                clickText("Fake LLM")
                clickText(confirm)
                waitForText(ask)
                return
            }
            if (hasText(cancel) || deviceHasText(cancel)) clickText(cancel)
        }
        waitForText(ask)
    }

    protected fun typeAndSend(message: String) {
        composeRule.onNode(hasSetTextAction()).performTextInput(message)
        composeRule.waitForIdle()
        val send = targetContext.getString(R.string.send)
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            runCatching {
                composeRule.onAllNodes(contentDescription(send) and isEnabled())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.onNode(contentDescription(send) and isEnabled()).performClick()
    }

    protected fun clickText(text: String, timeoutMs: Long = UI_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMs) { hasText(text) || deviceHasText(text) }
        if (hasText(text)) {
            composeRule.onAllNodesWithText(text)[0].scrollIntoView().performClick()
        } else {
            device().findObject(UiSelector().text(text)).click()
        }
        composeRule.waitForIdle()
    }

    protected fun clickContentDescription(description: String, timeoutMs: Long = UI_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMs) { hasContentDescription(description) || deviceHasDescription(description) }
        if (hasContentDescription(description)) {
            composeRule.onNodeWithContentDescription(description).performClick()
        } else {
            device().findObject(UiSelector().description(description)).click()
        }
        composeRule.waitForIdle()
    }

    protected fun waitForText(text: String, timeoutMs: Long = UI_TIMEOUT_MS, substring: Boolean = false) {
        composeRule.waitUntil(timeoutMs) {
            hasText(text, substring) || if (substring) deviceHasTextContaining(text) else deviceHasText(text)
        }
    }

    protected fun waitForContent(description: String, timeoutMs: Long = UI_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMs) { hasContentDescription(description) }
    }

    protected fun hasText(text: String, substring: Boolean = false): Boolean = try {
        composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    } catch (_: IllegalStateException) {
        false
    }

    protected fun hasContentDescription(description: String): Boolean = try {
        composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
    } catch (_: IllegalStateException) {
        false
    }

    protected fun optionalString(name: String): String? {
        val id = targetContext.resources.getIdentifier(name, "string", targetContext.packageName)
        if (id == 0) return null
        val raw = targetContext.resources.getText(id).toString()
        return runCatching { targetContext.getString(id) }.getOrDefault(raw)
    }

    protected fun clickTextContaining(fragment: String, timeoutMs: Long = UI_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMs) { hasText(fragment, substring = true) || deviceHasTextContaining(fragment) }
        if (hasText(fragment, substring = true)) {
            composeRule.onAllNodesWithText(fragment, substring = true)[0].scrollIntoView().performClick()
        } else {
            device().findObject(UiSelector().textContains(fragment)).click()
        }
        composeRule.waitForIdle()
    }

    protected fun assumeFeature(resourceName: String): String {
        val value = optionalString(resourceName)
        assumeTrue("Feature string $resourceName is not in this build yet", value != null)
        return checkNotNull(value)
    }

    protected fun grantIfDeclared(permission: String) {
        val info = targetContext.packageManager.getPackageInfo(
            targetContext.packageName,
            PackageManager.GET_PERMISSIONS
        )
        if (info.requestedPermissions?.contains(permission) != true) return
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            targetContext.packageName,
            permission
        )
    }

    protected fun classExists(name: String): Boolean = runCatching {
        Class.forName(name, false, targetContext.classLoader)
    }.isSuccess

    protected fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        descriptor.use { parcel ->
            return android.os.ParcelFileDescriptor.AutoCloseInputStream(parcel).readBytes().decodeToString()
        }
    }

    protected fun installFakeRecognizer(): String {
        val component = "${targetContext.packageName}/dev.chungjungsoo.gptmobile.qa.FakeRecognitionService"
        shell("settings put secure voice_recognition_service $component")
        return shell("settings get secure voice_recognition_service").trim()
    }

    protected fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun deviceHasText(text: String): Boolean = device().findObject(UiSelector().text(text)).exists()

    private fun deviceHasTextContaining(fragment: String): Boolean = device().findObject(UiSelector().textContains(fragment)).exists()

    private fun deviceHasDescription(description: String): Boolean = device().findObject(UiSelector().description(description)).exists()

    private fun SemanticsNodeInteraction.scrollIntoView(): SemanticsNodeInteraction {
        runCatching { performScrollTo() }
        return this
    }
}

object SmokeCaptures {
    @Volatile
    var currentTest: String = "smoke"

    fun note(feature: String, description: Description) {
        currentTest = "${description.className}.${description.methodName}"
        val line = "{\"class\":\"${description.className}\",\"method\":\"${description.methodName}\",\"feature\":\"$feature\"}\n"
        val directory = captureDirectory()
        File(directory, "smoke-features.jsonl").appendText(line)
    }

    fun capture(feature: String) {
        val directory = captureDirectory()
        val safeName = currentTest.substringAfterLast('.').replace(Regex("[^A-Za-z0-9._-]"), "_")
        val device = runCatching { UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()) }.getOrNull() ?: return
        device.takeScreenshot(File(directory, "$feature-$safeName.png"))
    }

    private fun captureDirectory(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = context.getExternalFilesDir(null)?.resolve("smoke-screenshots")
            ?: File(context.cacheDir, "smoke-screenshots")
        directory.mkdirs()
        return directory
    }
}

class SmokeDiagnosticsRule(private val feature: String) : TestWatcher() {
    override fun starting(description: Description) {
        SmokeCaptures.note(feature, description)
    }
}

fun deepLinkResolves(context: Context, uri: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(context.packageName)
    return context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
}

fun launchable(context: Context, className: String): Intent? {
    if (runCatching { Class.forName(className, false, context.classLoader) }.isFailure) return null
    return Intent().setClassName(context, className)
}
