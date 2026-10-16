package dev.chungjungsoo.gptmobile.data.agent.tool.code

import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunJavascriptToolTest {
    @Test
    fun `arithmetic returns the script value`() = runBlocking {
        val result = tool().execute("call-1", code("(1 + 2) * 3"))

        assertFalse(result.isError)
        assertEquals("9", json(result.content).string("text"))
    }

    @Test
    fun `const and arrow functions run`() = runBlocking {
        val result = tool().execute("call-1", code("const double = (n) => n * 2; double(4)"))

        assertFalse(result.isError)
        assertEquals("8", json(result.content).string("text"))
    }

    @Test
    fun `csv attachment can be summed with parseCsv`() = runBlocking {
        val javascript = RunJavascriptTool(
            attachments = listOf(
                JavascriptAttachment("sales.csv", "text/csv", "item,qty\napple,2\npear,3\n")
            )
        )

        val result = javascript.execute(
            "call-1",
            code(
                "var rows = parseCsv(attachments[0].text); var total = 0;" +
                    "for (var i = 1; i < rows.length; i++) total += Number(rows[i][1]); total"
            )
        )

        assertFalse(result.isError)
        assertEquals("5", json(result.content).string("text"))
    }

    @Test
    fun `chart spec is returned without extra fields`() = runBlocking {
        val result = tool().execute(
            "call-1",
            code(
                "chart({type:'bar', title:'Sales', labels:['a','b'], series:[{name:'s', data:[1, 2]}], evil:'<script>'}); 'ok'"
            )
        )

        assertFalse(result.isError)
        val model = json(result.content)
        val trace = json(result.traceContent!!)
        assertEquals("ok", model.string("text"))
        assertFalse(model.toString().contains("evil"))
        assertFalse(model.toString().contains("<script"))
        assertEquals(listOf("a", "b"), ChartSpec.parse(model["chart"]!!)!!.labels)
        assertEquals(listOf(1.0, 2.0), ChartSpec.parse(trace["chart"]!!)!!.series.single().data)
    }

    @Test
    fun `svg is kept in the trace and withheld from the model`() = runBlocking {
        val result = tool().execute(
            "call-1",
            code("svg('<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"><rect width=\"10\" height=\"10\"/></svg>'); 'drawn'")
        )

        assertFalse(result.isError)
        val model = json(result.content).toString()
        val trace = json(result.traceContent!!).toString()
        assertFalse(model.contains("<svg"))
        assertTrue(model.contains("svgCaptured"))
        assertTrue(trace.contains("<svg"))
        assertTrue(trace.contains("<rect"))
    }

    @Test
    fun `script tags are rejected and not returned`() = runBlocking {
        val result = tool().execute("call-1", code("svg('<svg><script>alert(1)</script></svg>')"))

        assertTrue(result.isError)
        val text = (result.content as ToolResultContent.Text).text
        assertFalse(text.contains("<script"))
        assertFalse(text.contains("alert"))
        assertNull(result.traceContent)
    }

    @Test
    fun `java network and files are unavailable`() = runBlocking {
        val sources = listOf(
            "java.lang.System.exit(0)",
            "Packages.java.io.File",
            "new java.io.File('/etc/passwd')",
            "fetch('https://example.com')",
            "readFile('/etc/passwd')",
            "(1).getClass()",
            "importClass(java.io.File)",
            "org.mozilla.javascript.Context.enter()"
        )
        sources.forEach { source ->
            val result = tool().execute("call-1", code(source))
            assertTrue(source, result.isError)
            val text = (result.content as ToolResultContent.Text).text
            assertFalse(text.contains("root:"))
            assertFalse(text.contains("/etc/passwd"))
        }
    }

    @Test
    fun `infinite loop stops on the instruction budget`() {
        val javascript = RunJavascriptTool(
            limits = JavascriptLimits(timeoutMillis = 500, maxInstructions = 20_000)
        )
        val started = System.nanoTime()
        val result = runBlocking { javascript.execute("call-1", code("while (true) {}")) }
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        assertTrue(result.isError)
        assertTrue((result.content as ToolResultContent.Text).text.contains("time limit"))
        assertTrue(elapsedMillis < 5_000)
    }

    @Test
    fun `oversized string repeat is rejected`() = runBlocking {
        val result = tool().execute("call-1", code("'x'.repeat(1000000)"))

        assertTrue(result.isError)
        assertTrue((result.content as ToolResultContent.Text).text.contains("too large"))
    }

    private fun tool() = RunJavascriptTool()

    private fun code(source: String) = buildJsonObject { put("code", source) }

    private fun json(content: ToolResultContent?): JsonObject = (content as ToolResultContent.Json).value as JsonObject

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
}
