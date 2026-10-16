package dev.chungjungsoo.gptmobile.presentation.ui.onboarding

import android.net.Uri
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.onboarding.MemoryOnboardingStore
import dev.chungjungsoo.gptmobile.data.onboarding.MiMoSuggestion
import dev.chungjungsoo.gptmobile.data.onboarding.OnboardingArchiveImport
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveFailure
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveOutcome
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveResult
import dev.chungjungsoo.gptmobile.data.repository.SecretMigrationError
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val store = MemoryOnboardingStore()
    private val repository = RecordingSettingRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saving the suggested key hands it to platform storage and clears the field`() {
        val viewModel = viewModel()
        viewModel.next()
        viewModel.updateApiKey("secret-key")

        viewModel.next()

        val saved = repository.added.single()
        assertEquals(MiMoSuggestion.NAME, saved.name)
        assertEquals(MiMoSuggestion.API_URL, saved.apiUrl)
        assertEquals(MiMoSuggestion.MODEL, saved.model)
        assertEquals("secret-key", saved.token)
        assertEquals(ClientType.CUSTOM, saved.compatibleType)
        assertTrue(saved.enabled)
        assertEquals(OnboardingStep.Import, viewModel.state.value.step)
        assertEquals("", viewModel.state.value.apiKey)
    }

    @Test
    fun `skip leaves the key unsaved and a failed save does not advance`() {
        val viewModel = viewModel()
        viewModel.next()
        viewModel.skip()

        assertTrue(repository.added.isEmpty())
        assertEquals(OnboardingStep.Import, viewModel.state.value.step)

        val failing = viewModel(RecordingSettingRepository(failAdd = true))
        failing.next()
        failing.updateApiKey("secret-key")
        failing.next()

        assertEquals(OnboardingStep.Provider, failing.state.value.step)
        assertTrue(failing.state.value.saveFailed)
        assertEquals("secret-key", failing.state.value.apiKey)
    }

    @Test
    fun `an existing MiMo endpoint is not saved twice`() {
        repository.existing = listOf(
            PlatformV2(
                name = MiMoSuggestion.NAME,
                compatibleType = ClientType.CUSTOM,
                enabled = true,
                apiUrl = MiMoSuggestion.API_URL,
                model = MiMoSuggestion.MODEL
            )
        )
        val viewModel = viewModel()
        viewModel.next()
        viewModel.updateApiKey("secret-key")

        viewModel.next()

        assertTrue(repository.added.isEmpty())
        assertEquals(OnboardingStep.Import, viewModel.state.value.step)
    }

    @Test
    fun `skip setup finishes at home without enabling voice`() {
        val viewModel = viewModel()

        viewModel.skip()

        assertTrue(store.completed)
        assertFalse(store.voice)
        assertEquals(OnboardingDestination.Home, viewModel.destination.replayCache.single())
    }

    @Test
    fun `skipping voice forgets the switch and opening bots keeps it`() {
        val skipped = viewModel()
        skipped.next()
        skipped.skip()
        skipped.skip()
        skipped.setVoiceEnabled(true)
        skipped.skip()
        skipped.next()

        assertFalse(store.voice)
        assertEquals(OnboardingDestination.Home, skipped.destination.replayCache.single())

        val openedStore = MemoryOnboardingStore()
        val opened = viewModel(store = openedStore)
        opened.next()
        opened.skip()
        opened.skip()
        opened.setVoiceEnabled(true)
        opened.next()
        opened.openBots()

        assertTrue(openedStore.voice)
        assertTrue(openedStore.completed)
        assertEquals(OnboardingDestination.Bots, opened.destination.replayCache.single())
    }

    @Test
    fun `a successful archive import is acknowledged and a failure is shown`() {
        val viewModel = viewModel()
        viewModel.next()
        viewModel.skip()

        viewModel.importArchive {
            ProjectArchiveOutcome.Success(
                ProjectArchiveResult(
                    projectIds = listOf(1),
                    createdProjects = 1,
                    updatedProjects = 0,
                    chatCount = 2,
                    fileCount = 3
                )
            )
        }

        assertEquals(1, viewModel.state.value.importedProjects)
        assertEquals(2, viewModel.state.value.importedChats)
        assertNull(viewModel.state.value.importFailure)
        assertEquals(OnboardingStep.Import, viewModel.state.value.step)

        viewModel.importArchive {
            ProjectArchiveOutcome.Failure(ProjectArchiveFailure.UNRECOGNIZED)
        }

        assertEquals(ProjectArchiveFailure.UNRECOGNIZED, viewModel.state.value.importFailure)
        assertNull(viewModel.state.value.importedProjects)
    }

    @Test
    fun `back returns to the previous step`() {
        val viewModel = viewModel()
        viewModel.next()

        viewModel.back()

        assertEquals(OnboardingStep.Welcome, viewModel.state.value.step)
    }

    private fun viewModel(
        settingRepository: RecordingSettingRepository = repository,
        store: MemoryOnboardingStore = this.store
    ) = OnboardingViewModel(settingRepository, store, IdleArchiveImport())
}

private class IdleArchiveImport : OnboardingArchiveImport {
    override suspend fun importZip(uri: Uri): ProjectArchiveOutcome =
        ProjectArchiveOutcome.Failure(ProjectArchiveFailure.UNREADABLE)

    override suspend fun importFolder(uri: Uri): ProjectArchiveOutcome =
        ProjectArchiveOutcome.Failure(ProjectArchiveFailure.UNREADABLE)
}

private class RecordingSettingRepository(
    private val failAdd: Boolean = false
) : SettingRepository {
    val added = mutableListOf<PlatformV2>()
    var existing: List<PlatformV2> = emptyList()

    override suspend fun fetchPlatforms(): List<Platform> = emptyList()

    override suspend fun fetchPlatformV2s(): List<PlatformV2> = existing

    override suspend fun fetchThemes(): ThemeSetting = ThemeSetting()

    override suspend fun migrateToPlatformV2() = Unit

    override suspend fun migrateSecrets(): List<SecretMigrationError> = emptyList()

    override suspend fun updatePlatforms(platforms: List<Platform>) = Unit

    override suspend fun updateThemes(themeSetting: ThemeSetting) = Unit

    override suspend fun addPlatformV2(platform: PlatformV2) {
        if (failAdd) throw IllegalStateException("Credential verification failed.")
        added += platform
    }

    override suspend fun updatePlatformV2(platform: PlatformV2) = Unit

    override suspend fun deletePlatformV2(platform: PlatformV2) = Unit

    override suspend fun getPlatformV2ById(id: Int): PlatformV2? = null
}
