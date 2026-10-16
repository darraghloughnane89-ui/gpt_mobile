package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemTest {

    @Test
    fun hermesSendState_flags_mapToTheFourStates() {
        assertEquals(HermesSendState.Sent, hermesSendState(canSend = true, isSending = true, sent = true))
        assertEquals(HermesSendState.Sending, hermesSendState(canSend = true, isSending = true, sent = false))
        assertEquals(HermesSendState.Armed, hermesSendState(canSend = true, isSending = false))
        assertEquals(HermesSendState.Idle, hermesSendState(canSend = false, isSending = false))
    }

    @Test
    fun loadingLineIndex_wrapsAndGuardsEmpty() {
        assertEquals(0, loadingLineIndex(0, 40))
        assertEquals(1, loadingLineIndex(41, 40))
        assertEquals(39, loadingLineIndex(-1, 40))
        assertEquals(0, loadingLineIndex(3, 0))
    }

    @Test
    fun drawerHidesUsageAndObsidian_paletteListsEveryDestination() {
        val drawer = drawerDestinations()
        assertFalse(drawer.contains(AppDestination.Usage))
        assertFalse(drawer.contains(AppDestination.Obsidian))
        assertTrue(drawer.contains(AppDestination.Chats))
        assertTrue(drawer.contains(AppDestination.Settings))
        assertEquals(AppDestination.entries.toSet(), paletteDestinations().toSet())
        assertTrue(paletteDestinations().containsAll(drawer))
        assertEquals("design/voice", AppDestination.Voice.route())
        assertEquals(AppSection.Library, AppDestination.Skills.section())
        assertEquals(AppSection.You, AppDestination.Usage.section())
    }

    @Test
    fun parseSimpleMarkdown_splitsProseAndFence() {
        val blocks = parseSimpleMarkdown("Hi\n```kotlin\nval x = 1\n```\nBye")
        assertEquals(3, blocks.size)
        assertEquals("Hi", (blocks[0] as MarkdownBlock.Paragraph).text)
        val code = blocks[1] as MarkdownBlock.Code
        assertEquals("kotlin", code.language)
        assertTrue(code.code.contains("val x"))
        assertEquals("Bye", (blocks[2] as MarkdownBlock.Paragraph).text)
        assertTrue(parseSimpleMarkdown("").isEmpty())
    }

    @Test
    fun emberOnBlack_andClayOnPaper_clearBodyContrast() {
        assertTrue(contrastRatio(Ember, Ink) >= 4.5f)
        assertTrue(contrastRatio(EmberDeep, Paper) >= 4.5f)
    }

    @Test
    fun productNameIsOnlyTheAppNameResource() {
        val module = locateModuleDir()
        val values = File(module, "src/main/res/values/strings.xml").readText()
        val names = Regex("""<string name="app_name">([^<]+)</string>""")
            .findAll(values)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(listOf("Robin"), names)
        File(module, "src/main/kotlin").walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                assertFalse(file.path, text.contains("GPT Mobile"))
                assertFalse(file.path, text.contains("\"Robin\""))
                assertFalse(file.path, text.contains("\"Rocco\""))
            }
        File(module, "src/main/res").walkTopDown()
            .filter { it.name == "strings.xml" && it.parentFile.name != "values" }
            .forEach { file ->
                assertFalse(file.path, file.readText().contains("name=\"app_name\""))
            }
    }

    private fun locateModuleDir(): File {
        val candidates = listOf(File("."), File("app"), File("src/main").parentFile?.parentFile)
        return candidates.filterNotNull().first { File(it, "src/main/res/values/strings.xml").isFile }
    }
}
