package dev.chungjungsoo.gptmobile.presentation.ui.library

import dev.chungjungsoo.gptmobile.data.onboarding.MemoryOnboardingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryTipViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `dismissing a tip keeps it dismissed`() {
        val store = MemoryOnboardingStore()
        val viewModel = LibraryTipViewModel(store)

        assertFalse(TipIds.CHATS in viewModel.dismissed.value)

        viewModel.dismiss(TipIds.CHATS)

        assertTrue(TipIds.CHATS in viewModel.dismissed.value)
        assertTrue(TipIds.CHATS in store.tips)
    }
}
