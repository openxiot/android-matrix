package cc.openxiot.wematrix.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.openxiot.wematrix.ui.theme.Blue500
import kotlin.math.roundToInt

/** 图表配色：深浅色主题下都足够醒目的固定色板 */
val ChartColors = listOf(
    Blue500,
    Color(0xFF34C759),
    Color(0xFFFF9500),
    Color(0xFFFF3B30),
    Color(0xFFAF52DE),
    Color(0xFF5AC8FA),
    Color(0xFFFFCC00),
    Color(0xFF8E8E93)
)

/** 环形图：左侧圆环 + 右侧类型图例（带数量） */
@Composable
fun DonutChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    val total = data.sumOf { it.second }
    val emptyColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val stroke = 20.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            if (total <= 0) {
                drawArc(
                    color = emptyColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke)
                )
            } else {
                var start = -90f
                data.forEachIndexed { index, (_, value) ->
                    val sweep = value.toFloat() / total * 360f
                    drawArc(
                        color = ChartColors[index % ChartColors.size],
                        startAngle = start + 1.5f,
                        sweepAngle = (sweep - 3f).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke)
                    )
                    start += sweep
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            data.forEachIndexed { index, (name, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ChartColors[index % ChartColors.size])
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/** 折线图：平滑曲线 + 面积渐变 + 虚线网格与坐标轴标签 */
@Composable
fun LineChart(
    xLabels: List<String>,
    yValues: List<Int>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val lineColor = MaterialTheme.colorScheme.primary
    val areaColor = lineColor.copy(alpha = 0.12f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        if (yValues.isEmpty() || xLabels.isEmpty()) return@Canvas
        val last = xLabels.lastIndex
        val labelPad = 34.dp.toPx()
        val bottomPad = 16.dp.toPx()
        val topPad = 6.dp.toPx()
        val rightPad = 8.dp.toPx()
        val plotLeft = labelPad
        val plotRight = size.width - rightPad
        val plotTop = topPad
        val plotBottom = size.height - bottomPad
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        val maxV = yValues.max().toFloat()
        val minV = yValues.min().toFloat()
        val range = if (maxV > minV) maxV - minV else 1f
        val yMin = (minV - range * 0.15f).coerceAtLeast(0f)
        val yMax = maxV + range * 0.1f
        val yRange = if (yMax > yMin) yMax - yMin else 1f

        // 水平网格线 + y 轴数值
        val gridCount = 4
        for (i in 0..gridCount) {
            val y = plotTop + plotHeight * i / gridCount
            drawLine(
                color = gridColor,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
            val value = (yMax - yRange * i / gridCount).roundToInt()
            val layout = textMeasurer.measure(AnnotatedString(value.toString()), labelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = value.toString(),
                topLeft = Offset(plotLeft - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f),
                style = labelStyle
            )
        }

        // x 轴标签：每隔 5 个显示一个，最后一个必显示
        xLabels.forEachIndexed { index, label ->
            if (index % 5 == 0 || index == last) {
                val x = plotLeft + plotWidth * index / last.toFloat()
                val layout = textMeasurer.measure(AnnotatedString(label), labelStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(x - layout.size.width / 2f, plotBottom + 4.dp.toPx()),
                    style = labelStyle
                )
            }
        }

        if (last == 0) return@Canvas

        val points = yValues.mapIndexed { index, v ->
            Offset(
                x = plotLeft + plotWidth * index / last.toFloat(),
                y = plotBottom - (v - yMin) / yRange * plotHeight
            )
        }

        // 面积
        val area = buildSmoothLine(
            points,
            start = Offset(points.first().x, plotBottom),
            end = Offset(points.last().x, plotBottom)
        )
        area.close()
        drawPath(area, areaColor)

        // 折线
        val line = buildSmoothLine(points)
        drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * 用 Catmull-Rom 转三次贝塞尔构造平滑路径。
 * start/end 传入时用于面积闭合（从底部起点进入、在底部终点退出）。
 */
private fun buildSmoothLine(points: List<Offset>, start: Offset? = null, end: Offset? = null): Path {
    val path = Path()
    if (points.isEmpty()) return path
    if (start != null) path.moveTo(start.x, start.y)
    path.lineTo(points.first().x, points.first().y)
    if (points.size < 3) {
        for (p in points.drop(1)) path.lineTo(p.x, p.y)
    } else {
        for (i in 0 until points.size - 1) {
            val p0 = points.getOrNull(i - 1) ?: points[i]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points.getOrNull(i + 2) ?: p2
            path.cubicTo(
                p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                p2.x, p2.y
            )
        }
    }
    if (end != null) path.lineTo(end.x, end.y)
    return path
}
