package dev.chungjungsoo.gptmobile.smoke

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription as contentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import dagger.hilt.android.EntryPointAccessors
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.presentation.GPTMobileApp
import dev.chungjungsoo.gptmobile.presentation.StartupDependencies
import dev.chungjungsoo.gptmobile.presentation.StartupRecoveryGate
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Tags a smoke test with the product feature it covers.
 * [scripts/test-report.sh] on the emulator harness reads the same name from the sidecar file.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SmokeFeature(val name: String)

/**
 * Shared Compose launch path for feature smokes.
 *
 * A missing feature class skips the test with a JUnit assumption. There is no [@org.junit.Ignore],
 * so the suite compiles and passes on main before that feature merges, then exercises the real UI.
 */
abstract class FeatureSmokeHarness(
    private val markerClass: String,
    private val feature: String
) {
    @get:Rule(order = 0)
    val composeRule = createEmptyComposeRule()

    @get:Rule(order = 1)
    val diagnostics = SmokeDiagnosticsRule(feature)

    protected val server = FakeChatServer()
    protected lateinit var scenario: ActivityScenario<out Activity>

    protected val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun prepareFeature() {
        assumeFeature(markerClass)
        grantNotifications()
        server.start()
        prepareAccounts()
    }

    @After
    fun finishFeature() {
        captureScreen()
        if (::scenario.isInitialized) {
            runCatching { scenario.close() }
        }
        server.close()
    }

    protected open fun prepareAccounts() {
        runBlocking {
            StartupRecoveryGate.await()
            val repository = repository()
            val existing = repository.fetchPlatformV2s()
            existing.filter { it.name == SECOND_PLATFORM }.forEach { repository.deletePlatformV2(it) }
            existing.filter { it.enabled && it.name != FAKE_PLATFORM }.forEach { platform ->
                repository.updatePlatformV2(platform.copy(enabled = false))
            }
            val current = repository.fetchPlatformV2s().firstOrNull { it.name == FAKE_PLATFORM }
            if (current == null) {
                repository.addPlatformV2(fakePlatform(server.baseUrl, FAKE_PLATFORM, "fake-model"))
            } else {
                repository.updatePlatformV2(
                    current.copy(
                        enabled = true,
                        apiUrl = server.baseUrl,
                        model = "fake-model",
                        token = SMOKE_TOKEN,
                        stream = true,
                        compatibleType = ClientType.CUSTOM
                    )
                )
            }
        }
    }

    protected fun seedSecondPlatform() {
        runBlocking {
            val repository = repository()
            val existing = repository.fetchPlatformV2s().firstOrNull { it.name == SECOND_PLATFORM }
            if (existing == null) {
                repository.addPlatformV2(fakePlatform(server.baseUrl, SECOND_PLATFORM, "fake-model"))
            } else {
                repository.updatePlatformV2(
                    existing.copy(
                        enabled = true,
                        apiUrl = server.baseUrl,
                        model = "fake-model",
                        token = SMOKE_TOKEN
                    )
                )
            }
        }
    }

    protected fun useModel(name: String, model: String) {
        runBlocking {
            val repository = repository()
            val platform = repository.fetchPlatformV2s().first { it.name == name }
            repository.updatePlatformV2(platform.copy(model = model, apiUrl = server.baseUrl, token = SMOKE_TOKEN))
        }
    }

    protected fun clearPlatforms() {
        runBlocking {
            StartupRecoveryGate.await()
            val repository = repository()
            repository.fetchPlatformV2s().forEach { repository.deletePlatformV2(it) }
        }
    }

    protected fun launchMain() {
        launch(Intent(context, MainActivity::class.java))
    }

    protected fun launch(intent: Intent) {
        scenario = ActivityScenario.launch(intent)
        composeRule.waitForIdle()
    }

    protected fun relaunchMain() {
        if (::scenario.isInitialized) scenario.close()
        launchMain()
    }

    protected fun waitForHome() {
        val newChat = context.getString(R.string.new_chat)
        val startChat = context.getString(R.string.start_a_chat)
        val projects = context.getString(R.string.projects)
        composeRule.waitUntil(HOME_TIMEOUT_MS) {
            hasText(newChat) || hasText(startChat) || hasText(projects) || deviceHasText(newChat) || deviceHasText(startChat)
        }
    }

    protected fun openNewChat() {
        val newChat = context.getString(R.string.new_chat)
        val startChat = context.getString(R.string.start_a_chat)
        val ask = context.getString(R.string.ask_a_question)
        val selectPlatform = context.getString(R.string.select_platform)
        waitForHome()
        val label = if (hasText(newChat) || deviceHasText(newChat)) newChat else startChat
        clickText(label)
        composeRule.waitUntil(HOME_TIMEOUT_MS) {
            hasText(ask) || hasText(selectPlatform) || deviceHasText(ask) || deviceHasText(selectPlatform)
        }
        if (hasText(selectPlatform) || deviceHasText(selectPlatform)) {
            if (hasText(FAKE_PLATFORM) || deviceHasText(FAKE_PLATFORM)) clickText(FAKE_PLATFORM)
            if (hasText(SECOND_PLATFORM) || deviceHasText(SECOND_PLATFORM)) clickText(SECOND_PLATFORM)
            clickText(context.getString(R.string.confirm))
        }
        waitForText(ask)
    }

    protected fun typeAndSend(message: String) {
        composeRule.onNode(hasSetTextAction()).performTextInput(message)
        composeRule.waitForIdle()
        val send = context.getString(R.string.send)
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            runCatching {
                composeRule.onAllNodes(contentDescription(send).and(isEnabled()))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.onNode(contentDescription(send).and(isEnabled())).performClick()
    }

    protected fun openSettings() {
        waitForHome()
        clickContentDescription(context.getString(R.string.settings))
        waitForText(context.getString(R.string.settings))
    }

    protected fun goBack() {
        clickContentDescription(context.getString(R.string.go_back))
    }

    protected fun clickText(text: String, timeoutMs: Long = UI_TIMEOUT_MS) {
        scrollToText(text)
        composeRule.waitUntil(timeoutMs) { hasText(text) || deviceHasText(text) }
        if (hasText(text)) {
            composeRule.onAllNodesWithText(text)[0].scrollIntoView().performClick()
        } else {
            device().findObject(UiSelector().text(text)).click()
        }
        composeRule.waitForIdle()
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

    protected fun longClickText(text: String) {
        scrollToText(text)
        composeRule.waitUntil(UI_TIMEOUT_MS) { hasText(text) }
        composeRule.onNodeWithText(text).performTouchInput { longClick(durationMillis = 700) }
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

    protected fun waitUntilGone(text: String, timeoutMs: Long = UI_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMs) { !hasText(text) && !deviceHasText(text) }
    }

    protected fun hasText(text: String, substring: Boolean = false): Boolean = try {
        composeRule.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    } catch (_: IllegalStateException) {
        false
    }

    protected fun hasContentDescription(description: String): Boolean = try {
        composeRule.onAllNodesWithContentDescription(description, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    } catch (_: IllegalStateException) {
        false
    }

    protected fun featureText(name: String, vararg args: Any): String {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        check(id != 0) { "Feature string $name is not in this build" }
        return if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
    }

    protected fun assumeFeature(className: String) {
        val present = runCatching { Class.forName(className, false, context.classLoader) }.isSuccess
        assumeTrue("Feature $className is not in this build yet", present)
    }

    protected fun classExists(className: String): Boolean = runCatching {
        Class.forName(className, false, context.classLoader)
    }.isSuccess

    protected fun grantIfDeclared(permission: String) {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        if (info.requestedPermissions?.contains(permission) != true) return
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, permission)
    }

    protected fun pressHome() {
        device().pressHome()
    }

    protected fun waitForNotification(
        timeoutMs: Long = UI_TIMEOUT_MS,
        predicate: (StatusBarNotification) -> Boolean
    ): StatusBarNotification {
        val manager = context.getSystemService(NotificationManager::class.java)
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            val found = manager.activeNotifications.firstOrNull(predicate)
            if (found != null) return found
            SystemClock.sleep(200)
        }
        val posted = manager.activeNotifications.joinToString { notification ->
            notification.notification.channelId ?: notification.packageName
        }
        error("Timed out waiting for a notification. Posted: $posted")
    }

    protected fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun repository(): SettingRepository = EntryPointAccessors.fromApplication(
        context.applicationContext as GPTMobileApp,
        StartupDependencies::class.java
    ).settingRepository()

    private fun grantNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        grantIfDeclared(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun scrollToText(text: String) {
        if (hasText(text) || deviceHasText(text)) return
        runCatching {
            UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text(text))
        }
    }

    private fun deviceHasText(text: String): Boolean = device().findObject(UiSelector().text(text)).exists()

    private fun deviceHasTextContaining(fragment: String): Boolean =
        device().findObject(UiSelector().textContains(fragment)).exists()

    private fun deviceHasDescription(description: String): Boolean =
        device().findObject(UiSelector().description(description)).exists()

    private fun SemanticsNodeInteraction.scrollIntoView(): SemanticsNodeInteraction {
        runCatching { performScrollTo() }
        return this
    }

    private fun captureScreen() {
        val directory = context.getExternalFilesDir(null)?.resolve("smoke-screenshots")
            ?: File(context.cacheDir, "smoke-screenshots")
        directory.mkdirs()
        val safeName = SmokeCaptures.currentTest.substringAfterLast('.').replace(Regex("[^A-Za-z0-9._-]"), "_")
        runCatching { device().takeScreenshot(File(directory, "$feature-$safeName.png")) }
    }

    private fun fakePlatform(apiUrl: String, name: String, model: String) = PlatformV2(
        name = name,
        compatibleType = ClientType.CUSTOM,
        enabled = true,
        apiUrl = apiUrl,
        token = SMOKE_TOKEN,
        model = model,
        stream = true,
        timeout = 30
    )

    companion object {
        const val FAKE_PLATFORM = "Fake LLM"
        const val SECOND_PLATFORM = "Fake LLM Two"
        const val HOME_TIMEOUT_MS = 45_000L
        const val UI_TIMEOUT_MS = 20_000L
    }
}

object SmokeCaptures {
    @Volatile
    var currentTest: String = "smoke"

    fun note(feature: String, description: Description) {
        currentTest = "${description.className}.${description.methodName}"
        val line = "{\"class\":\"${description.className}\",\"method\":\"${description.methodName}\",\"feature\":\"$feature\"}\n"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = context.getExternalFilesDir(null)?.resolve("smoke-screenshots")
            ?: File(context.cacheDir, "smoke-screenshots")
        directory.mkdirs()
        File(directory, "smoke-features.jsonl").appendText(line)
    }
}

class SmokeDiagnosticsRule(private val feature: String) : TestWatcher() {
    override fun starting(description: Description) {
        SmokeCaptures.note(feature, description)
    }
}
