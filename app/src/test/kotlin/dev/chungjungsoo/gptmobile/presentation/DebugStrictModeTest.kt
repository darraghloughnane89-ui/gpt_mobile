package dev.chungjungsoo.gptmobile.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugStrictModeTest {
    @Test
    fun strictModeInstallsOnlyForDebugBuilds() {
        var installed = false

        installStrictModeIfDebug(isDebug = false) { installed = true }
        assertFalse(installed)

        installStrictModeIfDebug(isDebug = true) { installed = true }
        assertTrue(installed)
    }
}
