package com.example.personalledger.presentation.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalledger.domain.stats.StatsBucket
import com.example.personalledger.domain.stats.StatsTypeFilter
import com.example.personalledger.presentation.format.toPersianDigits
import kotlin.math.max

@Composable
fun StatsLineChart(
    buckets: List<StatsBucket>,
    typeFilter: StatsTypeFilter,
    selectedIndex: Int?,
    incomeColor: Color,
    expenseColor: Color,
    gridColor: Color,
    labelColor: Color,
    markerColor: Color,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    /** When true (weekly/yearly), draw full axis labels rotated vertically under the chart. */
    verticalXLabels: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = remember(labelColor) {
        TextStyle(color = labelColor, fontSize = if (verticalXLabels) 9.sp else 10.sp)
    }

    val maxValue = remember(buckets, typeFilter) {
        buckets.maxOfOrNull { bucket ->
            when (typeFilter) {
                StatsTypeFilter.ALL -> max(bucket.incomeRial, bucket.expenseRial)
                StatsTypeFilter.INCOME -> bucket.incomeRial
                StatsTypeFilter.EXPENSE -> bucket.expenseRial
            }
        }?.coerceAtLeast(1L) ?: 1L
    }

    val chartHeight = if (verticalXLabels) 280.dp else 220.dp

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight)
            .pointerInput(buckets) {
                detectTapGestures { offset ->
                    if (buckets.isEmpty()) {
                        onSelect(null)
                        return@detectTapGestures
                    }
                    val left = 48.dp.toPx()
                    val right = size.width - 12.dp.toPx()
                    val usable = (right - left).coerceAtLeast(1f)
                    val step = if (buckets.size == 1) 0f else usable / (buckets.size - 1)
                    val idx = if (buckets.size == 1) {
                        0
                    } else {
                        ((offset.x - left) / step).toInt().coerceIn(0, buckets.lastIndex)
                    }
                    onSelect(idx)
                }
            },
    ) {
        val left = 48.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 16.dp.toPx()
        val bottomPad = if (verticalXLabels) 72.dp.toPx() else 28.dp.toPx()
        val bottom = size.height - bottomPad
        val chartH = (bottom - top).coerceAtLeast(1f)
        val chartW = (right - left).coerceAtLeast(1f)

        // Horizontal grid
        for (i in 0..3) {
            val y = top + chartH * i / 3f
            drawLine(gridColor.copy(alpha = 0.35f), Offset(left, y), Offset(right, y), strokeWidth = 1f)
            val value = maxValue * (3 - i) / 3
            val label = abbreviate(value).toPersianDigits()
            val layout = measurer.measure(label, labelStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(left - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f),
            )
        }

        if (buckets.isEmpty()) return@Canvas

        fun xFor(index: Int): Float =
            if (buckets.size == 1) left + chartW / 2f
            else left + chartW * index / (buckets.size - 1)

        fun yFor(amount: Long): Float =
            bottom - (amount.toFloat() / maxValue.toFloat()) * chartH

        fun drawSeries(values: List<Long>, color: Color) {
            if (values.isEmpty()) return
            val path = Path()
            val fill = Path()
            values.forEachIndexed { index, amount ->
                val x = xFor(index)
                val y = yFor(amount)
                if (index == 0) {
                    path.moveTo(x, y)
                    fill.moveTo(x, bottom)
                    fill.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fill.lineTo(x, y)
                }
            }
            fill.lineTo(xFor(values.lastIndex), bottom)
            fill.close()
            drawPath(
                fill,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)),
                    startY = top,
                    endY = bottom,
                ),
            )
            drawPath(path, color = color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }

        when (typeFilter) {
            StatsTypeFilter.ALL -> {
                drawSeries(buckets.map { it.incomeRial }, incomeColor)
                drawSeries(buckets.map { it.expenseRial }, expenseColor)
            }
            StatsTypeFilter.INCOME -> drawSeries(buckets.map { it.incomeRial }, incomeColor)
            StatsTypeFilter.EXPENSE -> drawSeries(buckets.map { it.expenseRial }, expenseColor)
        }

        val selected = selectedIndex
        if (selected != null && selected in buckets.indices) {
            val bucket = buckets[selected]
            val x = xFor(selected)
            drawLine(
                markerColor.copy(alpha = 0.55f),
                Offset(x, top),
                Offset(x, bottom),
                strokeWidth = 2.dp.toPx(),
            )
            val y = when (typeFilter) {
                StatsTypeFilter.EXPENSE -> yFor(bucket.expenseRial)
                StatsTypeFilter.INCOME -> yFor(bucket.incomeRial)
                StatsTypeFilter.ALL -> yFor(max(bucket.incomeRial, bucket.expenseRial))
            }
            drawCircle(markerColor, radius = 7.dp.toPx(), center = Offset(x, y))
            drawCircle(Color.White, radius = 3.5.dp.toPx(), center = Offset(x, y))
        }

        val labelStep = when {
            verticalXLabels -> 1
            buckets.size <= 8 -> 1
            buckets.size <= 16 -> 2
            buckets.size <= 31 -> 5
            else -> 1
        }
        buckets.forEachIndexed { index, bucket ->
            if (index % labelStep != 0 && index != buckets.lastIndex) return@forEachIndexed
            val label = bucket.axisLabel.toPersianDigits()
            val layout = measurer.measure(label, labelStyle)
            val cx = xFor(index)
            if (verticalXLabels) {
                // Rotate so full month names read upward under each tick.
                val pivot = Offset(cx, bottom + 8.dp.toPx() + layout.size.width / 2f)
                rotate(degrees = -90f, pivot = pivot) {
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            pivot.x - layout.size.width / 2f,
                            pivot.y - layout.size.height / 2f,
                        ),
                    )
                }
            } else {
                val x = cx - layout.size.width / 2f
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(x.coerceIn(left, right - layout.size.width), bottom + 6.dp.toPx()),
                )
            }
        }
    }
}

private fun abbreviate(value: Long): String = when {
    value >= 1_000_000_000L -> "${value / 1_000_000_000}B"
    value >= 1_000_000L -> "${value / 1_000_000}M"
    value >= 1_000L -> "${value / 1_000}K"
    else -> value.toString()
}
