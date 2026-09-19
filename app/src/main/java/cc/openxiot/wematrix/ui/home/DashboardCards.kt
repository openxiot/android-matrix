package cc.openxiot.wematrix.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.openxiot.wematrix.data.api.MobileDashboardWidget
import cc.openxiot.wematrix.ui.history.HistoryFieldChart
import cc.openxiot.wematrix.ui.history.buildHistorySeries
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 一张卡的渲染。按 `widget.type` 分派到五种具体卡，卡片壳统一：标题行 + 主体。
 *
 * **单卡失败不拖垮整屏**：某张卡取数失败时 `data` 为空、`error` 是人话，这里仍画出一张
 * 带标题的卡，主体换成一行警告 —— 而不是整屏报错或留白。
 *
 * 标题解析见 [DashboardTypes.resolveTitle]：`title`（用户数据）→ `titleKey` → 类型默认名。
 */
@Composable
fun DashboardWidgetHost(
    widget: MobileDashboardWidget,
    data: Map<String, Any?>?,
    error: String?,
    modifier: Modifier = Modifier
) {
    val title = DashboardTypes.resolveTitle(widget.title, widget.titleKey, widget.type)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (error != null) {
                ErrorBody(error)
            } else {
                when (widget.type) {
                    DashboardTypes.STAT -> StatView(data, metric = widget.config["metric"] as? String)
                    DashboardTypes.DISTRIBUTION -> DistributionView(data)
                    DashboardTypes.LINE -> LineView(data)
                    DashboardTypes.SERVICE -> ServiceView(data)
                    DashboardTypes.DEVICE -> DeviceView(data)
                    else -> EmptyBody("未知卡片类型")
                }
            }
        }
    }
}

@Composable
private fun ErrorBody(error: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
    }
}

// ===== 统计卡 =====

/**
 * 主数字。`alarms.today` 特殊：后端不给 `value`，只给整点桶 `hourly` + 服务端「现在」`to`，
 * 「今日」由前端按 `to` 落在的**本地自然日**折出（不能用设备自己的时钟，那是另一个钟）。
 * 其它 metric 直接读 `value`；缺键显示 `-`（与 0 不同：0 是真实读数，缺键才是「没数」）。
 */
@Composable
private fun StatView(data: Map<String, Any?>?, metric: String?) {
    val value = if (data == null) "-" else statValue(data)
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (value != "-") {
                StatUnit(metric)?.let { unit ->
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
        }
    }
}

private fun statValue(data: Map<String, Any?>): String {
    // alarms.today：折今日（hourly 里 at >= to 当日 00:00 的和）
    if (data.containsKey("hourly")) {
        val to = MobileRenderFormat.numberOf(data, "to") ?: return "-"
        val zone = ZoneId.systemDefault()
        val dayStart = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val sum = MobileRenderFormat.bucketsOf(data, "hourly")
            .filter { it.at >= dayStart }.sumOf { it.count }
        return sum.toString()
    }
    return MobileRenderFormat.numberOf(data, "value")?.toString() ?: "-"
}

/** 单位按 metric 口径（对齐 web `DASHBOARD_METRICS`）；告警/故障没有单位。 */
private fun StatUnit(metric: String?): String? = when (metric) {
    "devices.total", "devices.online" -> "台"
    "services.total" -> "个"
    else -> null
}

// ===== 数据分布 =====

@Composable
private fun DistributionView(data: Map<String, Any?>?) {
    val slices = data?.let { MobileRenderFormat.slicesOf(it, "groups") } ?: emptyList()
    if (slices.isEmpty()) {
        EmptyBody("暂无数据")
        return
    }
    DonutChart(slices.map { it.name to it.count })
}

// ===== 曲线 =====

/**
 * 两种源共用一个 dispatch：
 * - `alarmCount`：整点桶 `points:[{at,count}]`，值不会缺，按序连线（[LineChart]）；
 * - `serviceField`：真实的字段序列（真实时间轴、开关量阶梯、失败竖线），共享历史页那张图
 *   （[HistoryFieldChart]）。它以 `serviceId` / `maxPoints` 键存在与否区别于 alarmCount。
 */
@Composable
private fun LineView(data: Map<String, Any?>?) {
    if (data == null) {
        EmptyBody("尚未取到数")
        return
    }
    if (data.containsKey("serviceId")) {
        serviceFieldLine(data)
        return
    }
    val buckets = MobileRenderFormat.bucketsOf(data, "points")
    if (buckets.isEmpty()) {
        EmptyBody("暂无数据")
        return
    }
    LineChart(
        xLabels = buckets.map { hourLabel(it.at) },
        yValues = buckets.map { it.count },
        modifier = Modifier.fillMaxWidth().height(160.dp)
    )
}

private fun hourLabel(at: Long): String =
    DateTimeFormatter.ofPattern("HH:mm")
        .format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

@Composable
private fun serviceFieldLine(data: Map<String, Any?>) {
    val range = MobileRenderFormat.gsonRange(data)
    if (range.points.isEmpty() && range.carryIn == null) {
        EmptyBody("暂无数据")
        return
    }
    val failures = (data["failures"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() } ?: emptyList()
    val unit = data["unit"]?.toString() ?: ""
    val step = data["step"] == true
    val series = buildHistorySeries(range, failures)
    HistoryFieldChart(
        series = series,
        from = range.from,
        to = if (range.to > 0) range.to else (range.from + 1),
        unit = unit,
        showTimeAxis = false,
        step = step,
        modifier = Modifier.fillMaxWidth(),
        height = 160.dp
    )
}

// ===== 服务卡 =====

@Composable
private fun ServiceView(data: Map<String, Any?>?) {
    if (data == null) {
        EmptyBody("尚未取到数")
        return
    }
    val recordedAt = MobileRenderFormat.numberOf(data, "recordedAt")
    val error = MobileRenderFormat.stringOf(data, "error")
    val rows = MobileRenderFormat.serviceRowsOf(data, "fields")

    // 完全没有采集痕迹：明说「尚未采集」，别画一堆 `-`（后者看着像「采到了、恰好都空」）
    if (rows.isEmpty() && recordedAt == null) {
        EmptyBody("尚未采集")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.field,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = rowValue(row),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        // 时间角标 + 失败标识：值是最后一次**成功**的，失败与值可同存（不同设备卡）。
        if (recordedAt != null || error != null) {
            Text(
                text = buildList {
                    recordedAt?.let { add("采于 " + friendlyTime(it)) }
                    error?.let { add("⚠ $it") }
                }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = if (error != null)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun rowValue(row: MobileRenderFormat.ServiceRow): String {
    if (!row.hasValue) return "-"
    if (row.bit) return if (row.value == true) "开" else "关"
    val text = row.value?.toString() ?: return "-"
    return if (row.unit.isNotBlank()) "$text ${row.unit}" else text
}

private fun friendlyTime(ms: Long): String =
    DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

// ===== 设备卡 =====

/**
 * 三态由两个键在不在决定：
 * - 有 `value`：正常，读数是设备最后一次上报值；
 * - 有 `error`：读取失败；
 * - 都没有：尚未上报。
 * `value` 与 `error` **不并存**（与服务卡的 error+值同存的规则不同）。
 */
@Composable
private fun DeviceView(data: Map<String, Any?>?) {
    if (data == null) {
        EmptyBody("尚未取到数")
        return
    }
    val pid = MobileRenderFormat.stringOf(data, "pid") ?: ""
    val error = MobileRenderFormat.stringOf(data, "error")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = pid,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (error != null) error else
                if (data.containsKey("value")) (data["value"]?.toString() ?: "-") else "-",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ===== 共用壳 =====

@Composable
private fun EmptyBody(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}