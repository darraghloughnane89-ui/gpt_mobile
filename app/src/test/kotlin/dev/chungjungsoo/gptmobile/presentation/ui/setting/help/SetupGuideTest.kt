package dev.chungjungsoo.gptmobile.presentation.ui.setting.help

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupGuideTest {
    @Test
    fun parse_keepsSectionOrderStepNumbersAndCode() {
        val guide = SetupGuide.parse(
            """
            # Set up Robin

            Do this once.

            ## 1. Keystore

            1. Create the keystore.
            ```bash
            keytool -genkeypair
            ```
            2. Copy the SHA-1.

            - `APP_KEYSTORE`
            - `KEY_ALIAS`

            ## 2. Drive

            Use `drive.appdata` only.
            """.trimIndent()
        )

        assertEquals("Set up Robin", guide.title)
        assertEquals(listOf("1. Keystore", "2. Drive"), guide.sections.map { it.title })
        assertEquals("Do this once.", (guide.intro.single() as GuideBlock.Paragraph).text)
        val keystore = guide.sections.first().blocks
        val steps = keystore.filterIsInstance<GuideBlock.Steps>()
        assertEquals(listOf(1, 2), steps.flatMap { block -> block.items.map { it.number } })
        assertEquals("keytool -genkeypair", keystore.filterIsInstance<GuideBlock.Code>().single().text)
        val bullets = keystore.filterIsInstance<GuideBlock.Bullets>().single().items
        assertEquals(listOf("`APP_KEYSTORE`", "`KEY_ALIAS`"), bullets)
        assertEquals(
            "drive.appdata",
            (guide.sections[1].blocks.single() as GuideBlock.Paragraph).text.substringAfter('`').substringBefore('`')
        )
    }

    @Test
    fun parseGuideInline_keepsCodeBoldAndLinks() {
        val spans = parseGuideInline("Paste **the token** into `Robin vault`. See [Console](https://console.cloud.google.com/).")

        assertEquals("Paste ", spans[0].text)
        assertEquals("the token", spans[1].text)
        assertTrue(spans[1].strong)
        assertEquals(" into ", spans[2].text)
        assertEquals("Robin vault", spans[3].text)
        assertTrue(spans[3].code)
        assertEquals(". See ", spans[4].text)
        assertEquals("Console (https://console.cloud.google.com/)", spans[5].text)
    }

    @Test
    fun shippedGuide_matchesTheAssetAndCoversEverySetup() {
        val root = repoRoot()
        val document = File(root, "docs/SETUP.md").readText()
        val asset = File(root, "app/src/main/assets/setup.md").readText()
        assertEquals(document, asset)

        val guide = SetupGuide.parse(document)
        assertEquals("Set up Robin", guide.title)
        assertEquals(
            listOf(
                "1. Release signing keystore",
                "2. Google Drive backup",
                "3. Obsidian vault",
                "4. Hermes on the Mac",
                "5. Plugins",
                "6. Phone control",
                "7. Import the CBRE project"
            ),
            guide.sections.map { it.title }
        )
        val text = document
        assertTrue(text.contains("dev.chungjungsoo.gptmobile"))
        assertTrue(text.contains("https://www.googleapis.com/auth/drive.appdata"))
        assertTrue(text.contains("darraghloughnane89-ui/knowledge-vault"))
        assertTrue(text.contains("APP_KEYSTORE"))
        assertTrue(text.contains("cbre-admin"))
        assertTrue(text.contains(":8642"))
        assertTrue(text.contains("Notification access"))
        assertTrue(text.contains("Import zip"))
        assertTrue(text.contains("robin-project"))
        guide.sections.forEach { section ->
            val numbers = section.blocks.filterIsInstance<GuideBlock.Steps>().flatMap { block ->
                block.items.map { it.number }
            }
            assertTrue(section.title, numbers.isNotEmpty())
            assertEquals(section.title, 1, numbers.first())
        }
    }

    private fun repoRoot(): File {
        val start = System.getProperty("user.dir") ?: error("user.dir is unset")
        var dir: File? = File(start).absoluteFile
        while (dir != null) {
            if (File(dir, "docs/SETUP.md").isFile && File(dir, "settings.gradle.kts").isFile) {
                return dir
            }
            dir = dir.parentFile
        }
        error("Could not find the repo root from $start")
    }
}
