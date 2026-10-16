package dev.chungjungsoo.gptmobile.presentation.fastentry

import android.content.Context
import android.widget.Toast
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.fastentry.FastEntryPlanner

fun NavHostController.routeTemplates(): Set<String> {
    val found = mutableSetOf<String>()
    val pending = ArrayDeque<NavDestination>()
    pending.add(graph)
    while (pending.isNotEmpty()) {
        val node = pending.removeFirst()
        node.route?.let(found::add)
        if (node is NavGraph) {
            node.forEach(pending::add)
        }
    }
    return found
}

suspend fun collectFastEntries(
    navController: NavHostController,
    context: Context,
    enabledPlatforms: suspend () -> List<String>,
    rememberBot: suspend (String?) -> Unit,
    chatExists: suspend (Int) -> Boolean,
    refreshShortcuts: () -> Unit
) {
    FastEntrySignals.requests.collect { request ->
        if (request == null) return@collect
        FastEntrySignals.consume()
        val exists = if (request.action == FastEntryPlanner.Action.OPEN_CHAT) {
            chatExists(request.chatId)
        } else {
            true
        }
        if (!exists) refreshShortcuts()
        val destination = FastEntryPlanner.resolve(
            request = request,
            enabledPlatforms = enabledPlatforms(),
            registeredRoutes = navController.routeTemplates(),
            chatExists = exists
        )
        if (request.action == FastEntryPlanner.Action.NEW_CHAT && destination is FastEntryPlanner.Destination.Chat) {
            rememberBot(request.botUid)
        }
        when (destination) {
            is FastEntryPlanner.Destination.Chat -> openRoute(
                navController,
                context,
                destination.route,
                R.string.fast_entry_missing_chat
            )

            is FastEntryPlanner.Destination.Voice -> openRoute(
                navController,
                context,
                destination.route,
                R.string.voice_mode_unavailable
            )

            FastEntryPlanner.Destination.QuickAsk -> context.startActivity(FastEntryIntents.quickAsk(context))
            FastEntryPlanner.Destination.VoiceUnavailable ->
                Toast.makeText(context, R.string.voice_mode_unavailable, Toast.LENGTH_LONG).show()

            FastEntryPlanner.Destination.MissingPlatforms ->
                Toast.makeText(context, R.string.fast_entry_no_platform, Toast.LENGTH_LONG).show()

            FastEntryPlanner.Destination.MissingChat ->
                Toast.makeText(context, R.string.fast_entry_missing_chat, Toast.LENGTH_LONG).show()

            FastEntryPlanner.Destination.Ignore -> Unit
        }
    }
}

private fun openRoute(navController: NavHostController, context: Context, route: String, failureMessage: Int) {
    runCatching { navController.navigate(route) }
        .onFailure { Toast.makeText(context, failureMessage, Toast.LENGTH_LONG).show() }
}
