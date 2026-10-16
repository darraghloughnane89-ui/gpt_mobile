package dev.chungjungsoo.gptmobile.data.phone

import java.util.zip.Deflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextExtractorTest {
    @Test
    fun extract_literalText_returnsShowingOperators() {
        val pdf = pdfWithStream("BT (Hello PDF) Tj ET")

        assertEquals("Hello PDF", PdfTextExtractor.extract(pdf))
    }

    @Test
    fun extract_escapedLiteral_decodesOctalAndParentheses() {
        val pdf = pdfWithStream("(A\\(B\\) \\101) Tj")

        assertEquals("A(B) A", PdfTextExtractor.extract(pdf))
    }

    @Test
    fun extract_textArray_insertsSpaceForLargeNegativeAdjustment() {
        val pdf = pdfWithStream("[(Hello) -200 (world)] TJ")

        assertEquals("Hello world", PdfTextExtractor.extract(pdf))
    }

    @Test
    fun extract_hexString_decodesAscii() {
        val pdf = pdfWithStream("<4869> Tj")

        assertEquals("Hi", PdfTextExtractor.extract(pdf))
    }

    @Test
    fun extract_flateDecodeStream_inflatesText() {
        val commands = "BT (Compressed page) Tj ET".toByteArray(Charsets.ISO_8859_1)
        val pdf = pdfWithStream(deflate(commands), "/Filter /FlateDecode")

        assertEquals("Compressed page", PdfTextExtractor.extract(pdf))
    }

    @Test
    fun extract_imageOnlyStream_returnsEmpty() {
        val pdf = pdfWithStream("q 100 0 0 100 0 0 cm /Im0 Do Q")

        assertEquals("", PdfTextExtractor.extract(pdf))
    }

    @Test
    fun extract_longText_respectsMaxChars() {
        val pdf = pdfWithStream("(${"a".repeat(50)}) Tj")

        assertEquals("aaaa", PdfTextExtractor.extract(pdf, maxChars = 4))
    }

    private fun pdfWithStream(stream: String, dictExtra: String = ""): ByteArray =
        pdfWithStream(stream.toByteArray(Charsets.ISO_8859_1), dictExtra)

    private fun pdfWithStream(stream: ByteArray, dictExtra: String): ByteArray {
        val header = "%PDF-1.4\n1 0 obj\n<< /Length ${stream.size} $dictExtra >>\nstream\n"
        val footer = "\nendstream\nendobj\n%%EOF"
        return header.toByteArray(Charsets.ISO_8859_1) + stream + footer.toByteArray(Charsets.ISO_8859_1)
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()
        val buffer = ByteArray(data.size + 64)
        val size = deflater.deflate(buffer)
        deflater.end()
        assertTrue(size > 0)
        return buffer.copyOf(size)
    }
}
