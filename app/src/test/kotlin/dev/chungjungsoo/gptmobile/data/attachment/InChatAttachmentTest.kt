package dev.chungjungsoo.gptmobile.data.attachment

import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.context.TokenEstimator
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.dto.openai.common.ImageContent
import dev.chungjungsoo.gptmobile.data.dto.openai.common.ImageUrl
import dev.chungjungsoo.gptmobile.data.dto.openai.common.Role
import dev.chungjungsoo.gptmobile.data.dto.openai.common.TextContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatMessage
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.util.FileUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InChatAttachmentTest {
    @Test
    fun plainTextIsChunkedAndMarkedWhenItExceedsTheCharacterLimit() {
        val text = (1..5).joinToString("\n\n") { index -> "paragraph-$index " + "word ".repeat(20) }
        val extraction = DocumentTextExtractor.extract(
            bytes = text.toByteArray(),
            mimeType = "text/plain",
            displayName = "notes.txt",
            limits = DocumentTextLimits(maxBytes = 1_000_000, maxCharacters = 80, chunkCharacters = 40)
        ) as DocumentExtraction.Ready

        assertTrue(extraction.truncated)
        assertTrue(extraction.chunkCount > 1)
        assertTrue(extraction.contextText.contains("[Attached file: notes.txt]"))
        assertTrue(extraction.contextText.contains("[Part 1/${extraction.chunkCount}]"))
        assertTrue(extraction.contextText.contains("[Truncated to 80 characters.]"))
    }

    @Test
    fun pdfDocxAndXlsxTextBecomeModelContext() {
        val pdf = DocumentTextExtractor.extract(pdf("Hello PDF"), "application/pdf", "scan.pdf") as DocumentExtraction.Ready
        val compressed = DocumentTextExtractor.extract(flatePdf("Compressed page"), "application/pdf", "scan.pdf") as DocumentExtraction.Ready
        val docx = DocumentTextExtractor.extract(docx("Hello DOCX", "Second line"), "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "notes.docx") as DocumentExtraction.Ready
        val xlsx = DocumentTextExtractor.extract(xlsx(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "sheet.xlsx") as DocumentExtraction.Ready

        assertTrue(pdf.contextText.contains("Hello PDF"))
        assertTrue(compressed.contextText.contains("Compressed page"))
        assertTrue(docx.contextText.contains("Hello DOCX"))
        assertTrue(docx.contextText.contains("Second line"))
        assertTrue(xlsx.contextText.contains("Name"))
        assertTrue(xlsx.contextText.contains("Ada"))
        assertTrue(xlsx.contextText.contains("42"))
    }

    @Test
    fun oversizedOrEmptyDocumentsAreRejected() {
        val limits = DocumentTextLimits(maxBytes = 4, maxCharacters = 100, chunkCharacters = 20)
        val oversized = DocumentTextExtractor.extract("too-long".toByteArray(), "text/plain", "a.txt", limits) as DocumentExtraction.Rejected
        val emptyPdf = DocumentTextExtractor.extract("%PDF-1.4\n".toByteArray(), "application/pdf", "empty.pdf") as DocumentExtraction.Rejected

        assertTrue(oversized.reason.contains("20") || oversized.reason.contains("cannot be attached") || oversized.reason.contains("MB"))
        assertTrue(emptyPdf.reason.contains("No extractable text"))
    }

    @Test
    fun documentTextIsPrependedAndImagesStayOnTheVisionWireFormat() {
        val attachment = ChatAttachment(
            localFilePath = "notes.txt",
            preparedFilePath = "notes.txt",
            displayName = "notes.txt",
            mimeType = "text/plain",
            sizeBytes = 12,
            extractedText = "[Attached file: notes.txt]\n[Part 1/1]\nBudget is 42"
        )
        val visible = modelContextText("What is the budget?", listOf(attachment))

        assertTrue(visible.startsWith("[Attached file: notes.txt]"))
        assertTrue(visible.endsWith("What is the budget?"))

        val payload = NetworkClient.openAIJson.encodeToString(
            ChatMessage(
                role = Role.USER,
                content = listOf(
                    TextContent("look"),
                    ImageContent(ImageUrl(openAiImageDataUrl("image/jpeg", "aGVsbG8=")))
                )
            )
        )
        assertTrue(payload.contains("\"type\":\"image_url\""))
        assertTrue(payload.contains("\"url\":\"data:image/jpeg;base64,aGVsbG8=\""))
    }

    @Test
    fun miMoUsesChatCompletionsAndImageModelsUseTheImagesEndpoint() {
        assertEquals(
            ProviderTransport.OPENAI_CHAT,
            providerTransport(ClientType.CUSTOM, VisionHosts.miMoApiUrl(), "mimo-v2-omni")
        )
        assertEquals(
            ProviderTransport.OPENAI_CHAT,
            providerTransport(ClientType.OPENAI, "https://token-plan-ams.xiaomimimo.com/v1/", "mimo-v2-pro")
        )
        assertEquals(
            ProviderTransport.OPENAI_RESPONSES,
            providerTransport(ClientType.OPENAI, "https://api.openai.com/v1/", "gpt-5.6")
        )
        assertEquals(
            ProviderTransport.OPENAI_IMAGES,
            providerTransport(ClientType.OPENAI, "https://api.openai.com/v1/", "gpt-image-1")
        )
        assertEquals(
            ProviderTransport.OPENAI_IMAGES,
            providerTransport(ClientType.OPENROUTER, "https://openrouter.ai/api/v1/", "dall-e-3")
        )
        assertEquals(
            ProviderTransport.GOOGLE,
            providerTransport(ClientType.GOOGLE, "https://generativelanguage.googleapis.com/", "gemini-2.5-flash-image")
        )
        assertEquals(
            ProviderTransport.OPENAI_CHAT,
            providerTransport(ClientType.GROQ, "https://api.groq.com/openai/v1/", "meta-llama/llama-4-scout-17b-16e-instruct")
        )
        assertEquals(
            ProviderTransport.OPENAI_CHAT,
            providerTransport(ClientType.OLLAMA, "http://localhost:11434/v1/", "llava")
        )
        assertTrue(ImageGeneration.isGeminiImageModel("gemini-2.5-flash-image"))
        assertFalse(ImageGeneration.usesOpenAiImagesEndpoint(ClientType.CUSTOM, "gpt-image-1", VisionHosts.miMoApiUrl()))
    }

    @Test
    fun imageGenerationRequestKeepsGptImageOffTheDalleResponseFormat() {
        val json = Json {
            encodeDefaults = true
            explicitNulls = false
        }
        val gptImage = json.encodeToString(imageGenerationRequest("gpt-image-1", "a red bicycle"))
        val dalle = json.encodeToString(imageGenerationRequest("dall-e-3", "a red bicycle"))

        assertTrue(gptImage.contains("\"size\":\"1024x1024\""))
        assertFalse(gptImage.contains("response_format"))
        assertTrue(dalle.contains("\"response_format\":\"b64_json\""))
    }

    @Test
    fun generatedImagesRoundTripAndSharePlansSeparateTextFromImages() {
        val directory = File(createTempDirectory("generated-images").toString())
        val payload = GeneratedImagePayload("image/png", Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4)))
        val attachment = GeneratedImageFiles.write(directory, payload, nowMillis = 10)

        assertNotNull(attachment)
        assertEquals("image/png", attachment!!.mimeType)
        assertTrue(attachment.localFilePath.endsWith("generated_10.png"))
        assertEquals(byteArrayOf(1, 2, 3, 4).toList(), java.io.File(attachment.localFilePath).readBytes().toList())
        assertNull(decodeGeneratedImage("not-an-image"))
        assertEquals("image/jpeg", decodeGeneratedImage("data:image/jpeg;base64,aGVsbG8=")!!.mimeType)

        val plan = answerSharePlan("Answer", listOf("/tmp/a.png", "/tmp/notes.pdf", "/tmp/b.JPG"))
        assertTrue(plan.sharesText)
        assertEquals(listOf("/tmp/a.png", "/tmp/b.JPG"), plan.imagePaths)
        assertFalse(answerSharePlan("   ", listOf("/tmp/notes.pdf")).sharesImages)
    }

    @Test
    fun documentTokensCountExtractedTextAndAttachableTypesIncludeOfficeFiles() {
        val document = ChatAttachment(
            localFilePath = "notes.txt",
            preparedFilePath = "notes.txt",
            displayName = "notes.txt",
            mimeType = "text/plain",
            sizeBytes = 20,
            extractedText = "alpha beta gamma delta"
        )
        val turn = ConversationTurn(
            userMessage = MessageV2(content = "q", platformType = null, attachments = listOf(document)),
            assistantMessage = null,
            isCurrentTurn = true
        )

        assertTrue(TokenEstimator.estimateTurn(turn) > TokenEstimator.estimateText("q"))
        assertTrue(FileUtils.isAttachableMimeType("application/pdf"))
        assertTrue(FileUtils.isAttachableMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertTrue(FileUtils.isAttachableMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        assertTrue(FileUtils.isAttachableMimeType("text/plain"))
        assertTrue(FileUtils.isAttachableMimeType("image/png"))
        assertFalse(FileUtils.isAttachableMimeType("application/zip"))
        assertFalse(FileUtils.isSupportedUploadMimeType("application/pdf"))
    }

    private fun pdf(text: String): ByteArray {
        val stream = "BT /F1 12 Tf 100 100 Td ($text) Tj ET\n"
        return """
            %PDF-1.4
            4 0 obj
            << /Length ${stream.length} >>
            stream
            $stream
            endstream
            endobj
        """.trimIndent().toByteArray()
    }

    private fun flatePdf(text: String): ByteArray {
        val stream = "BT ($text) Tj ET".toByteArray()
        val deflater = Deflater()
        val compressed = ByteArrayOutputStream()
        deflater.setInput(stream)
        deflater.finish()
        val buffer = ByteArray(256)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            if (count > 0) compressed.write(buffer, 0, count)
        }
        deflater.end()
        val data = compressed.toByteArray()
        val header = "4 0 obj\n<< /Filter /FlateDecode /Length ${data.size} >>\nstream\n".toByteArray()
        return header + data + "\nendstream\nendobj\n".toByteArray()
    }

    private fun docx(first: String, second: String): ByteArray = zip(
        "word/document.xml" to """
            <?xml version="1.0" encoding="UTF-8"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>$first</w:t></w:r></w:p>
                <w:p><w:r><w:t>$second</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()
    )

    private fun xlsx(): ByteArray = zip(
        "xl/sharedStrings.xml" to """
            <sst>
              <si><t>Name</t></si>
              <si><t>Ada</t></si>
            </sst>
        """.trimIndent(),
        "xl/worksheets/sheet1.xml" to """
            <worksheet>
              <sheetData>
                <row r="1">
                  <c r="A1" t="s"><v>0</v></c>
                  <c r="B1" t="s"><v>1</v></c>
                </row>
                <row r="2"><c r="A2"><v>42</v></c></row>
              </sheetData>
            </worksheet>
        """.trimIndent()
    )

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
