package dev.chungjungsoo.gptmobile.presentation.ui.main

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.Platform

enum class StartupRoute {
    INTRO,
    MIGRATE,
    HOME
}

/**
 * First launch with nothing configured opens onboarding.
 * A finished onboarding that skipped every provider still goes home.
 * Legacy installs that already have a token or an enabled platform keep the migration path.
 */
fun resolveStartupRoute(
    legacyPlatforms: List<Platform>,
    platformV2s: List<PlatformV2>,
    onboardingCompleted: Boolean
): StartupRoute {
    val legacyUninitialized = legacyPlatforms.all { platform -> !platform.enabled } &&
        legacyPlatforms.all { platform -> platform.token == null }
    return when {
        platformV2s.isEmpty() && legacyUninitialized && !onboardingCompleted -> StartupRoute.INTRO
        platformV2s.isEmpty() && !legacyUninitialized -> StartupRoute.MIGRATE
        else -> StartupRoute.HOME
    }
}
