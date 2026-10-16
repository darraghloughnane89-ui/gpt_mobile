package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.agent.tool.code.ChartSpec
import dev.chungjungsoo.gptmobile.data.agent.tool.code.ChartType
import kotlin.math.min

@Composable
internal fun ChartSpecCanvas(
    spec: ChartSpec,
    modifier: Modifier = Modifier
) {
    val description = stringResource(
        R.string.code_execution_chart_description,
        spec.type.name.lowercase(),
        spec.title.ifBlank { stringResource(R.string.run_javascript) }
    )
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error
    )
    Column(modifier = modifier.semantics { contentDescription = description }) {
        if (spec.title.isNotEmpty()) {
            Text(
                text = spec.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(top = 8.dp)
        ) {
            when (spec.type) {
                ChartType.BAR -> drawBars(spec, palette)
                ChartType.LINE -> drawLines(spec, palette)
                ChartType.PIE -> drawPie(spec, palette)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            spec.series.forEachIndexed { index, series ->
                Canvas(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                ) {
                    drawRect(palette[index % palette.size])
                }
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBars(spec: ChartSpec, palette: List<Color>) {
    val values = spec.series.flatMap { it.data }
    val minValue = minOf(0.0, values.minOrNull() ?: 0.0)
    val maxValue = maxOf(0.0, values.maxOrNull() ?: 0.0)
    val span = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0
    val groups = spec.labels.size.coerceAtLeast(1)
    val seriesCount = spec.series.size.coerceAtLeast(1)
    val groupWidth = size.width / groups
    val barWidth = groupWidth / (seriesCount + 1)
    fun yOf(value: Double) = size.height - ((value - minValue) / span * size.height).toFloat()
    val zeroY = yOf(0.0)
    spec.series.forEachIndexed { seriesIndex, series ->
        series.data.forEachIndexed { index, value ->
            val top = min(yOf(value), zeroY)
            val bottom = maxOf(yOf(value), zeroY)
            val left = index * groupWidth + (seriesIndex + 0.5f) * barWidth
            drawRect(
                color = palette[seriesIndex % palette.size],
                topLeft = Offset(left, top),
                size = Size(barWidth * 0.8f, (bottom - top).coerceAtLeast(1f))
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLines(spec: ChartSpec, palette: List<Color>) {
    val values = spec.series.flatMap { it.data }
    val minValue = minOf(0.0, values.minOrNull() ?: 0.0)
    val maxValue = maxOf(0.0, values.maxOrNull() ?: 0.0)
    val span = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0
    val groups = spec.labels.size.coerceAtLeast(1)
    fun yOf(value: Double) = size.height - ((value - minValue) / span * size.height).toFloat()
    spec.series.forEachIndexed { seriesIndex, series ->
        val path = Path()
        series.data.forEachIndexed { index, value ->
            val x = (index + 0.5f) / groups * size.width
            val y = yOf(value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = palette[seriesIndex % palette.size],
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPie(spec: ChartSpec, palette: List<Color>) {
    val values = spec.series.firstOrNull()?.data.orEmpty().map { it.coerceAtLeast(0.0) }
    val total = values.sum()
    if (total <= 0.0) return
    val diameter = min(size.width, size.height)
    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
    val arcSize = Size(diameter, diameter)
    var start = -90f
    values.forEachIndexed { index, value ->
        val sweep = (value / total * 360.0).toFloat()
        drawArc(
            color = palette[index % palette.size],
            startAngle = start,
            sweepAngle = sweep,
            useCenter = true,
            topLeft = topLeft,
            size = arcSize
        )
        start += sweep
    }
}
