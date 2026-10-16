package dev.chungjungsoo.gptmobile.data.agent.tool.code

import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpreadsheetTextTest {
    @Test
    fun parseCsv_readsQuotesAndNewlines() {
        val rows = SpreadsheetText.parseCsv("item,\"say \"\"hi\"\"\",qty\napple,2,1\n")

        assertEquals(listOf(listOf("item", "say \"hi\"", "qty"), listOf("apple", "2", "1")), rows)
    }

    @Test
    fun extract_turnsFirstSheetIntoCsv() {
        val bytes = xlsx(
            shared = """
                <sst>
                  <si><t>name</t></si>
                  <si><t>ada</t></si>
                </sst>
            """.trimIndent(),
            sheet = """
                <worksheet>
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1"><v>2</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>1</v></c>
                      <c r="B2"><v>3</v></c>
                    </row>
                  </sheetData>
                </worksheet>
            """.trimIndent()
        )

        assertEquals(
            "name,2\nada,3",
            SpreadsheetText.extract("book.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes)
        )
    }

    @Test
    fun selectSpreadsheetTexts_readsCsvAndSkipsOversizedWorkbooks() {
        val csv = attachment("sales.csv", "text/csv", 20)
        val huge = attachment("huge.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", SpreadsheetText.MAX_XLSX_BYTES + 1L)
        val reads = mutableListOf<String>()

        val selected = selectSpreadsheetTexts(listOf(csv, huge)) { path, _ ->
            reads += path
            "a,b\n1,2".toByteArray()
        }

        assertEquals(listOf("sales.csv"), reads)
        assertEquals("a,b\n1,2", selected.single().text)
        assertNull(selected.find { it.name == "huge.xlsx" })
    }

    private fun attachment(name: String, mimeType: String, size: Long) = ChatAttachment(
        localFilePath = name,
        preparedFilePath = "",
        displayName = name,
        mimeType = mimeType,
        sizeBytes = size
    )

    private fun xlsx(shared: String, sheet: String): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write(shared.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheet.toByteArray())
            zip.closeEntry()
        }
        return buffer.toByteArray()
    }
}
