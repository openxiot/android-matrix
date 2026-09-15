package cc.openxiot.wematrix.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.openxiot.wematrix.data.api.ModbusHistoryRange
import cc.openxiot.wematrix.ui.modbus.axisTimeText
import cc.openxiot.wematrix.ui.modbus.bucketNumeric
import cc.openxiot.wematrix.ui.modbus.carryInNumeric
import cc.openxiot.wematrix.ui.modbus.isBucket
import cc.openxiot.wematrix.ui.modbus.numberText
import cc.openxiot.wematrix.ui.modbus.sampleNumeric
import cc.openxiot.wematrix.ui.theme.Red

/**
 * 一个字段的曲线图，对齐 web 的 `device.service.history.charts.ts`（ECharts 那份配置）。
 *
 * **不复用首页的 `LineChart`**：那张是「24 个等距整点、值不会缺、只要好看」的看板图
 * （平滑曲线 + 面积渐变 + 按序号均分的横轴），而这一张要的是**如实**：真实时间轴（窗口定死
 * `[from, to]`，不是按点的序号均分）、开关量走阶梯、降采样时画均值 + 最小/最大两条虚线、
 * 采集失败打竖线。两者共同的可复用处只剩「画网格」那一层，硬凑成一个函数会把两个都写坏。
 *
 * 这里只画，数据的摆法（carryIn 补点、桶取均值、非数值跳过）在 [buildHistorySeries] 里。
 */

/**
 * 一张图的三个序列（x = 毫秒时刻，y = 值）。
 *
 * `min` / `max` 只在降采样时才有内容 —— 原始样本没有段的概念，一条线就是全部。
 * [failures] 是同一方法在窗口内的失败时刻，画成竖虚线。
 */
data class HistorySeries(
    /** 采样值 / 桶均值：实线 */
    val avg: List<Pair<Long, Double>>,
    /** 桶内最小值：虚线（仅降采样） */
    val min: List<Pair<Long, Double>>,
    /** 桶内最大值：虚线（仅降采样） */
    val max: List<Pair<Long, Double>>,
    /** 降采样：多画那两条虚线 */
    val downsampled: Boolean,
    /** 该方法在窗口内的采集失败时刻 */
    val failures: List<Long>
)

/**
 * 把一次 `/history/range` 的响应摆成 [HistorySeries]。
 *
 * 两处照 web 的 `historyFieldOption`：
 * - **carryIn 补在 `from` 那一刻**（三条线都补）：不补的话曲线从窗口内第一条采样点才开始，
 *   左边缘凭空缺一截，看起来像「那段时间没采到」；
 * - **非数值的点直接丢掉**（不是补 null）：字符串 / 取值表的描述串画不成曲线，
 *   丢掉之后相邻两点直接相连 —— 与 ECharts 那边「数组里根本没有这个点」的表现一致。
 */
fun buildHistorySeries(range: ModbusHistoryRange, failures: List<Long>): HistorySeries {
    val avg = mutableListOf<Pair<Long, Double>>()
    val min = mutableListOf<Pair<Long, Double>>()
    val max = mutableListOf<Pair<Long, Double>>()

    carryInNumeric(range)?.let { carry ->
        avg += range.from to carry
        min += range.from to carry
        max += range.from to carry
    }

    range.points.forEach { point ->
        if (isBucket(point)) {
            // 降采样：一行 = 一段，三个数各画各的（非数值字段的三个统计量都是 null，自然跳过）
            bucketNumeric(point)?.let { avg += point.at to it }
            point.min?.let { min += point.at to it }
            point.max?.let { max += point.at to it }
        } else {
            sampleNumeric(point)?.let { avg += point.at to it }
        }
    }

    return HistorySeries(avg, min, max, range.downsampled, failures)
}

/**
 * 画一张字段曲线图。
 *
 * 横轴**定死在 [from] ~ [to]**（不是按点的序号均分）：采样是「值变了才记一条」，点距本就是
 * 时间距，按序号均分会把「停了两小时没变」画成和「每 5 秒一跳」一样宽。
 *
 * [showTimeAxis] 只在最下面那张图为 true：几张图纵向排开、时间轴对齐，刻度重复画没意义。
 */
@Composable
fun HistoryFieldChart(
    series: HistorySeries,
    from: Long,
    to: Long,
    unit: String,
    showTimeAxis: Boolean,
    step: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val lineColor = MaterialTheme.colorScheme.primary
    val bandColor = lineColor.copy(alpha = 0.45f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val failureColor = Red

    Canvas(modifier = modifier.then(Modifier.height(height))) {
        val labelPad = 52.dp.toPx()
        val bottomPad = if (showTimeAxis) 22.dp.toPx() else 8.dp.toPx()
        val topPad = 14.dp.toPx()
        val rightPad = 10.dp.toPx()
        val plotLeft = labelPad
        val plotRight = size.width - rightPad
        val plotTop = topPad
        val plotBottom = size.height - bottomPad
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop
        // 窗口可以是零宽（自定义只选了一天时不会，但 from == to 在理论上仍可能出现）：别拿它做除数
        val span = (to - from).coerceAtLeast(1L)

        // 值域：三条线一起算（只算均值线的话，降采样时最小/最大那两条会画到框外）。
        // 一条点都没有时给一个占位区间，好让网格与坐标照样画出来 —— 空图不该是一片空白
        val all = if (series.downsampled) series.avg + series.min + series.max else series.avg
        val rawMin = all.minOfOrNull { it.second }
        val rawMax = all.maxOfOrNull { it.second }
        val yMin: Double
        val yMax: Double
        if (rawMin == null || rawMax == null) {
            yMin = 0.0
            yMax = 1.0
        } else if (rawMax > rawMin) {
            // 上下各留一成：贴着上下边缘的折点会被裁掉一半
            val pad = (rawMax - rawMin) * 0.1
            yMin = rawMin - pad
            yMax = rawMax + pad
        } else {
            // 全程一个值（开关量长期不动就是这样）：撑开 ±1，线落在正中间
            yMin = rawMin - 1.0
            yMax = rawMax + 1.0
        }
        val yRange = yMax - yMin

        fun xOf(at: Long): Float =
            plotLeft + plotWidth * ((at - from).toFloat() / span.toFloat())

        fun yOf(value: Double): Float =
            plotBottom - (plotHeight * ((value - yMin) / yRange)).toFloat()

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
            val value = yMax - yRange * i / gridCount
            val text = numberText(value)
            val layout = textMeasurer.measure(AnnotatedString(text), labelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = text,
                topLeft = Offset(plotLeft - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f),
                style = labelStyle
            )
        }

        // 单位（服务端数据，**原样显示、不翻译**）挂在左上角：web 是 yAxis 的 name，位置同一个意思
        if (unit.isNotEmpty()) {
            drawText(
                textMeasurer = textMeasurer,
                text = unit,
                topLeft = Offset(plotLeft, 0f),
                style = labelStyle
            )
        }

        if (showTimeAxis) {
            // 四道刻度（含两端）：时刻按窗口跨度选粒度，与 web 的 axisTime 同口径
            val ticks = 4
            for (i in 0..ticks) {
                val at = from + span * i / ticks
                val text = axisTimeText(at, span)
                val layout = textMeasurer.measure(AnnotatedString(text), labelStyle)
                // 首尾两个贴着边框往里收，免得被裁掉
                val x = (plotLeft + plotWidth * i / ticks - layout.size.width / 2f)
                    .coerceIn(0f, size.width - layout.size.width)
                drawText(
                    textMeasurer = textMeasurer,
                    text = text,
                    topLeft = Offset(x, plotBottom + 4.dp.toPx()),
                    style = labelStyle
                )
            }
        }

        // 采集失败：竖虚线铺满绘图区。它标的是「这条错误首次出现的时刻」，
        // 同一条错误持续存在不会重复记 —— 明细在页面下面的「采集异常」里
        series.failures.forEach { at ->
            val x = xOf(at)
            if (x in plotLeft..plotRight) {
                drawLine(
                    color = failureColor,
                    start = Offset(x, plotTop),
                    end = Offset(x, plotBottom),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                )
            }
        }

        // 降采样时才有的两条虚线：每桶的最小 / 最大，如实表达「这一段是这么走的」，
        // 而不是拿均值假装成采样点。先画它们，均值线压在上面
        if (series.downsampled) {
            drawPointLine(series.max, ::xOf, ::yOf, bandColor, 1.dp.toPx(), dashed = true)
            drawPointLine(series.min, ::xOf, ::yOf, bandColor, 1.dp.toPx(), dashed = true)
        }

        drawPointLine(series.avg, ::xOf, ::yOf, lineColor, 2.dp.toPx(), dashed = false, step = step)
    }
}

/**
 * 把一串点连成线。
 *
 * 开关量是阶梯变化（0 一直保持到下一次翻转），直连会画出根本不存在的中间值 ——
 * 故 [step] 时先横走到下一个时刻再竖直跳，与 web 的 `step: 'end'` 同形。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPointLine(
    points: List<Pair<Long, Double>>,
    xOf: (Long) -> Float,
    yOf: (Double) -> Float,
    color: Color,
    width: Float,
    dashed: Boolean,
    step: Boolean = false
) {
    if (points.isEmpty()) return
    val path = Path()
    points.forEachIndexed { index, (at, value) ->
        val x = xOf(at)
        val y = yOf(value)
        if (index == 0) {
            path.moveTo(x, y)
        } else if (step) {
            path.lineTo(x, yOf(points[index - 1].second))
            path.lineTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = width,
            cap = StrokeCap.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5f, 5f)) else null
        )
    )
}
