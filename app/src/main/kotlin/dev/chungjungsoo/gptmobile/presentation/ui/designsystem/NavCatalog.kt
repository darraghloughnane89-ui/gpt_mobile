package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import dev.chungjungsoo.gptmobile.presentation.common.Route

/**
 * Top-level places. Feature streams replace a shell by pointing [route] at their screen.
 * Usage and Obsidian stay out of the drawer; they live under Settings and the command palette.
 */
enum class AppDestination {
    Chats,
    Voice,
    Projects,
    Bots,
    Skills,
    Memory,
    Review,
    Settings,
    Usage,
    Obsidian
}

enum class AppSection {
    Talk,
    Library,
    Inbox,
    You
}

object DesignRoute {
    const val VOICE = "design/voice"
    const val PROJECTS = "design/projects"
    const val SKILLS = "design/skills"
    const val MEMORY = "design/memory"
    const val REVIEW = "design/review"
    const val USAGE = "design/usage"
    const val OBSIDIAN = "design/obsidian"
}

fun AppDestination.section(): AppSection = when (this) {
    AppDestination.Chats, AppDestination.Voice -> AppSection.Talk
    AppDestination.Projects, AppDestination.Bots, AppDestination.Skills, AppDestination.Memory -> AppSection.Library
    AppDestination.Review -> AppSection.Inbox
    AppDestination.Settings, AppDestination.Usage, AppDestination.Obsidian -> AppSection.You
}

fun AppDestination.route(): String = when (this) {
    AppDestination.Chats -> Route.CHAT_LIST
    AppDestination.Voice -> DesignRoute.VOICE
    AppDestination.Projects -> DesignRoute.PROJECTS
    AppDestination.Bots -> Route.BOTS
    AppDestination.Skills -> DesignRoute.SKILLS
    AppDestination.Memory -> DesignRoute.MEMORY
    AppDestination.Review -> DesignRoute.REVIEW
    AppDestination.Settings -> Route.SETTING_ROUTE
    AppDestination.Usage -> DesignRoute.USAGE
    AppDestination.Obsidian -> DesignRoute.OBSIDIAN
}

fun drawerDestinations(): List<AppDestination> = listOf(
    AppDestination.Chats,
    AppDestination.Voice,
    AppDestination.Projects,
    AppDestination.Bots,
    AppDestination.Skills,
    AppDestination.Memory,
    AppDestination.Review,
    AppDestination.Settings
)

fun paletteDestinations(): List<AppDestination> = AppDestination.entries

fun shellDestinations(): List<AppDestination> = listOf(
    AppDestination.Voice,
    AppDestination.Projects,
    AppDestination.Skills,
    AppDestination.Memory,
    AppDestination.Review,
    AppDestination.Usage,
    AppDestination.Obsidian
)
