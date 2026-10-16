package dev.chungjungsoo.gptmobile.presentation.ui.main

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.ClientType
import org.junit.Assert.assertEquals
import org.junit.Test

class StartupRouteTest {
    @Test
    fun `a fresh install opens onboarding and a skipped install opens home`() {
        assertEquals(StartupRoute.INTRO, resolveStartupRoute(emptyList(), emptyList(), onboardingCompleted = false))
        assertEquals(StartupRoute.HOME, resolveStartupRoute(emptyList(), emptyList(), onboardingCompleted = true))
    }

    @Test
    fun `existing providers skip onboarding and legacy tokens still migrate`() {
        val platform = PlatformV2(
            name = "MiMo",
            compatibleType = ClientType.CUSTOM,
            apiUrl = "https://token-plan-ams.xiaomimimo.com/v1",
            model = "mimo-v2.6-pro"
        )
        assertEquals(
            StartupRoute.HOME,
            resolveStartupRoute(emptyList(), listOf(platform), onboardingCompleted = false)
        )
        assertEquals(
            StartupRoute.MIGRATE,
            resolveStartupRoute(
                legacyPlatforms = listOf(Platform(name = ApiType.OPENAI, token = "legacy", enabled = false)),
                platformV2s = emptyList(),
                onboardingCompleted = true
            )
        )
        assertEquals(
            StartupRoute.MIGRATE,
            resolveStartupRoute(
                legacyPlatforms = listOf(Platform(name = ApiType.OPENAI, enabled = true)),
                platformV2s = emptyList(),
                onboardingCompleted = false
            )
        )
    }
}
