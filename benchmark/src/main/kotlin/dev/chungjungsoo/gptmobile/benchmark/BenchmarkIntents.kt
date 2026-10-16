package dev.chungjungsoo.gptmobile.benchmark

import android.content.Intent

internal const val TARGET_PACKAGE = "dev.chungjungsoo.gptmobile"
internal const val TARGET_ACTIVITY = "dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity"

// Keep these in sync with BenchmarkChatSeeder and CHAT_MESSAGE_LIST_TAG.
internal const val BENCHMARK_ACTION = "dev.chungjungsoo.gptmobile.benchmark.SCENARIO"
internal const val EXTRA_SCENARIO = "scenario"
internal const val SCENARIO_SCROLL = "scroll"
internal const val SCENARIO_STREAM = "stream"
internal const val MESSAGE_LIST_TAG = "chat_message_list"

internal fun scenarioIntent(scenario: String): Intent = Intent().apply {
    setClassName(TARGET_PACKAGE, TARGET_ACTIVITY)
    action = BENCHMARK_ACTION
    putExtra(EXTRA_SCENARIO, scenario)
}
