package dev.chungjungsoo.gptmobile.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "dev.chungjungsoo.gptmobile"

/**
 * Title of the seeded chat used by scroll and stream journeys.
 * Keep in sync with docs/PERFORMANCE.md.
 */
internal const val LONG_CHAT_TITLE = "Benchmark long chat"

internal object BenchmarkTags {
    const val CHAT_LIST = "chat_list"
    const val CHAT_ROW = "chat_row"
    const val MESSAGE_LIST = "message_list"
    const val CHAT_INPUT = "chat_input"
    const val SEND_MESSAGE = "send_message"
    const val REPLY_STREAMING = "reply_streaming"
}

internal fun MacrobenchmarkScope.waitForChatList() {
    val appeared = device.wait(Until.hasObject(By.res(packageName, BenchmarkTags.CHAT_LIST)), 15_000)
    if (!appeared) {
        error("Chat list did not appear. Finish setup on the device before benchmarking.")
    }
}

internal fun MacrobenchmarkScope.openLongChat() {
    waitForChatList()
    val titled = device.findObject(By.text(LONG_CHAT_TITLE))
    val row = titled ?: device.findObject(By.res(packageName, BenchmarkTags.CHAT_ROW))
        ?: error(
            "No chat row. Create a chat titled \"$LONG_CHAT_TITLE\" before this journey. " +
                "See docs/PERFORMANCE.md."
        )
    row.click()
    val opened = device.wait(Until.hasObject(By.res(packageName, BenchmarkTags.MESSAGE_LIST)), 10_000)
    if (!opened) {
        error("Opened a chat but the message list did not appear.")
    }
}

internal fun MacrobenchmarkScope.scrollLongChat() {
    val list = device.findObject(By.res(packageName, BenchmarkTags.MESSAGE_LIST))
        ?: error("Message list is not on screen.")
    list.setGestureMargin(device.displayWidth / 5)
    repeat(3) {
        list.fling(Direction.UP)
        device.waitForIdle()
    }
    repeat(2) {
        list.fling(Direction.DOWN)
        device.waitForIdle()
    }
}

internal fun MacrobenchmarkScope.typePrompt(prompt: String) {
    val input = device.findObject(By.res(packageName, BenchmarkTags.CHAT_INPUT))
        ?: error("Chat input is not on screen.")
    input.click()
    input.text = prompt
    if (input.text.isNullOrBlank()) {
        device.executeShellCommand("input text ${prompt.replace(" ", "%s")}")
    }
}

internal fun MacrobenchmarkScope.sendAndWaitForStream() {
    val send = device.findObject(By.res(packageName, BenchmarkTags.SEND_MESSAGE))
        ?: error("Send button is not on screen.")
    send.click()
    val streaming = device.wait(Until.hasObject(By.res(packageName, BenchmarkTags.REPLY_STREAMING)), 20_000)
    if (!streaming) {
        error(
            "Reply did not start streaming. The seeded chat needs a working provider. " +
                "See docs/PERFORMANCE.md."
        )
    }
    device.wait(Until.gone(By.res(packageName, BenchmarkTags.REPLY_STREAMING)), 90_000)
}
