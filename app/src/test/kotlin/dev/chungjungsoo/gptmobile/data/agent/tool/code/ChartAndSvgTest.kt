package dev.chungjungsoo.gptmobile.data.agent.tool.code

import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartAndSvgTest {
    @Test
    fun chartSpec_acceptsAlignedSeriesAndDropsUnsafeValues() {
        val chart = ChartSpec.parse(
            buildJsonObject {
                put("type", "line")
                put("title", "Trend")
                put(
                    "labels",
                    buildJsonArray {
                        add(JsonPrimitive("Mon"))
                        add(JsonPrimitive("Tue"))
                    }
                )
                put(
                    "series",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("name", "Visits")
                                put(
                                    "data",
                                    buildJsonArray {
                                        add(JsonPrimitive(1))
                                        add(JsonPrimitive(2))
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )

        assertEquals(ChartType.LINE, chart!!.type)
        assertEquals(listOf(1.0, 2.0), chart.series.single().data)
        assertNull(
            ChartSpec.parse(
                buildJsonObject {
                    put("type", "pie")
                    put(
                        "series",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("name", "bad")
                                    put("data", buildJsonArray { add(JsonPrimitive(Double.POSITIVE_INFINITY)) })
                                }
                            )
                        }
                    )
                }
            )
        )
    }

    @Test
    fun svgSanitizer_keepsShapesAndDropsActiveContent() {
        val safe = """<svg xmlns="http://www.w3.org/2000/svg" width="8" height="8"><circle r="2"/></svg>"""
        assertEquals(safe, SvgArtifact.sanitize(safe))
        assertNull(SvgArtifact.sanitize("""<svg><script>alert(1)</script></svg>"""))
        assertNull(SvgArtifact.sanitize("""<svg onload="alert(1)"></svg>"""))
        assertNull(SvgArtifact.sanitize("""<svg><image href="https://example.com/a.png"/></svg>"""))
        assertNull(SvgArtifact.sanitize("""<svg><!DOCTYPE svg [<!ENTITY x "y">]></svg>"""))
    }

    @Test
    fun codeExecutionView_readsCodeChartAndSanitizedSvg() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg"><rect width="4" height="4"/></svg>"""
        val event = ToolEvent(
            eventId = "event-1",
            runId = "run-1",
            sequence = 0,
            callId = "call-1",
            connectionUidSnapshot = null,
            connectionNameSnapshot = null,
            toolName = BuiltInAgentTool.RUN_JAVASCRIPT,
            modelToolName = BuiltInAgentTool.RUN_JAVASCRIPT,
            arguments = """{"code":"1+1"}""",
            result = buildJsonObject {
                put("text", "2")
                put(
                    "chart",
                    buildJsonObject {
                        put("type", "bar")
                        put("labels", buildJsonArray { add(JsonPrimitive("a")) })
                        put(
                            "series",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("name", "s")
                                        put("data", buildJsonArray { add(JsonPrimitive(2)) })
                                    }
                                )
                            }
                        )
                    }
                )
                put("svg", svg)
            }.toString(),
            resultType = "JSON",
            status = ToolEventStatus.COMPLETED
        )

        val view = event.toCodeExecutionView()

        assertTrue(event.isJavascriptTool())
        assertEquals("1+1", view.code)
        assertEquals("2", view.text)
        assertEquals(ChartType.BAR, view.chart!!.type)
        assertEquals(svg, view.svg)
        assertFalse(view.svgRejected)
    }
}
