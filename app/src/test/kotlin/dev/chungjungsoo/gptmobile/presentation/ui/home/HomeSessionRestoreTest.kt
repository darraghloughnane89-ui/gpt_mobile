package dev.chungjungsoo.gptmobile.presentation.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSessionRestoreTest {
    @Test
    fun searchModeRequiresASavedQuery() {
        val restored = restoreHomeSearch(query = "robin", searchMode = true)
        assertEquals("robin", restored.query)
        assertTrue(restored.searchMode)

        val blank = restoreHomeSearch(query = "  ", searchMode = true)
        assertFalse(blank.searchMode)

        val closed = restoreHomeSearch(query = "robin", searchMode = false)
        assertEquals("robin", closed.query)
        assertFalse(closed.searchMode)

        val missing = restoreHomeSearch(query = null, searchMode = null)
        assertEquals("", missing.query)
        assertFalse(missing.searchMode)
    }
}
