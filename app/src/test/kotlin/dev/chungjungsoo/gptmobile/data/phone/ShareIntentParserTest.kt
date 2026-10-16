package dev.chungjungsoo.gptmobile.data.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareIntentParserTest {
    @Test
    fun parse_sendText_keepsPlainText() {
        val parsed = ShareIntentParser.parse(
            ShareIntentParser.Snapshot(
                action = ShareIntentParser.ACTION_SEND,
                mimeType = "text/plain",
                extraText = "  Summarize this  "
            )
        )

        assertEquals("Summarize this", parsed.text)
        assertTrue(parsed.imageUris.isEmpty())
        assertTrue(parsed.pdfUris.isEmpty())
    }

    @Test
    fun parse_sendImage_classifiesImageUri() {
        val parsed = ShareIntentParser.parse(
            ShareIntentParser.Snapshot(
                action = ShareIntentParser.ACTION_SEND,
                mimeType = "image/jpeg",
                extraText = null,
                streamUris = listOf("content://media/1"),
                streamMimeTypes = listOf("image/jpeg")
            )
        )

        assertEquals(listOf("content://media/1"), parsed.imageUris)
    }

    @Test
    fun parse_sendPdf_classifiesPdfEvenWhenTypeIsGeneric() {
        val parsed = ShareIntentParser.parse(
            ShareIntentParser.Snapshot(
                action = ShareIntentParser.ACTION_SEND,
                mimeType = "application/octet-stream",
                extraText = null,
                streamUris = listOf("content://docs/report.pdf"),
                streamMimeTypes = listOf(null)
            )
        )

        assertEquals(listOf("content://docs/report.pdf"), parsed.pdfUris)
    }

    @Test
    fun parse_sendMultiple_splitsImagesPdfsAndRejectedItems() {
        val parsed = ShareIntentParser.parse(
            ShareIntentParser.Snapshot(
                action = ShareIntentParser.ACTION_SEND_MULTIPLE,
                mimeType = "*/*",
                extraText = "Look at these",
                streamUris = listOf(
                    "content://media/photo",
                    "content://docs/notes.pdf",
                    "content://docs/sheet.xlsx"
                ),
                streamMimeTypes = listOf("image/png", "application/pdf", "application/vnd.ms-excel")
            )
        )

        assertEquals("Look at these", parsed.text)
        assertEquals(listOf("content://media/photo"), parsed.imageUris)
        assertEquals(listOf("content://docs/notes.pdf"), parsed.pdfUris)
        assertEquals(1, parsed.rejected)
    }

    @Test
    fun parse_portableFile_isNotChatText() {
        val parsed = ShareIntentParser.parse(
            ShareIntentParser.Snapshot(
                action = ShareIntentParser.ACTION_SEND,
                mimeType = "text/plain",
                extraText = null,
                streamUris = listOf("content://docs/field.robin-bot"),
                streamMimeTypes = listOf("text/plain")
            )
        )

        assertEquals(listOf("content://docs/field.robin-bot"), parsed.portableUris)
        assertTrue(parsed.textFileUris.isEmpty())
        assertTrue(parsed.text.isEmpty())
    }

    @Test
    fun parse_unknownAction_isEmpty() {
        val parsed = ShareIntentParser.parse(
            ShareIntentParser.Snapshot(
                action = "android.intent.action.VIEW",
                mimeType = "text/plain",
                extraText = "nope"
            )
        )

        assertTrue(parsed.isEmpty())
    }
}
