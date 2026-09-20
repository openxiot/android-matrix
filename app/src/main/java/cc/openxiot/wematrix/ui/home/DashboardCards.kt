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
                    // 下面这三个开关（显示片数 / 失败竖线 / 显示单位）是**显示决策**，
                    // 后端取数层不读它们（`DashboardWidgetDataService` 一处都不看，
                    // 全量下发分组、照样给 failures 与 unit）—— 所以在这里生效，改开关不必重取数。
                    DashboardTypes.DISTRIBUTION -> DistributionView(
                        data = data,
                        limit = DashboardTypes.configInt(widget.config, "limit")
                    )
                    DashboardTypes.LINE -> LineView(
                        data = data,
                        showFailureShadow = DashboardTypes.configBool(widget.config, "showFailureShadow", true)
                    )
                    DashboardTypes.SERVICE -> ServiceView(
                        data = data,
                        showUnit = DashboardTypes.configBool(widget.config, "showUnit", true),
                        // 半宽卡不画「采于 …」那一行（[serviceShowsRecordedAt]）：并排两张半宽卡
                        // 高度要尽量一样，而这一行正是服务卡比统计卡多出来的那点高度
                        half = widget.size == DashboardTypes.SIZE_HALF
                    )
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

/**
 * 环形图。`limit`（「显示片数」）是**显示层**的截断：后端全量下发、不打上限也不读这个键，
 * 所以「前 N + 其他」在这里折（[truncateSlices]，与 web `truncatePoints` 同边界）。
 */
@Composable
private fun DistributionView(data: Map<String, Any?>?, limit: Int?) {
    val all = data?.let { MobileRenderFormat.slicesOf(it, "groups") } ?: emptyList()
    if (all.isEmpty()) {
        EmptyBody("暂无数据")
        return
    }
    DonutChart(truncateSlices(all, limit, "其他").map { it.name to it.count })
}

// ===== 曲线 =====

/**
 * 两种源共用一个 dispatch：
 * - `alarmCount`：整点桶 `points:[{at,count}]`，值不会缺，按序连线（[LineChart]）；
 * - `serviceField`：真实的字段序列（真实时间轴、开关量阶梯、失败竖线），共享历史页那张图
 *   （[HistoryFieldChart]）。它以 `serviceId` / `maxPoints` 键存在与否区别于 alarmCount。
 */
@Composable
private fun LineView(data: Map<String, Any?>?, showFailureShadow: Boolean) {
    if (data == null) {
        EmptyBody("尚未取到数")
        return
    }
    if (data.containsKey("serviceId")) {
        serviceFieldLine(data, showFailureShadow)
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
private fun serviceFieldLine(data: Map<String, Any?>, showFailureShadow: Boolean) {
    val range = MobileRenderFormat.gsonRange(data)
    if (range.points.isEmpty() && range.carryIn == null) {
        EmptyBody("暂无数据")
        return
    }
    // 关掉竖线时：取数层照样把 failures 发下来（后端不看这个开关 —— 「画不画」是展示决策），
    // 画不画由这里定：给个空清单就是「一条都不画」。
    val failures = if (showFailureShadow) {
        (data["failures"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() } ?: emptyList()
    } else {
        emptyList()
    }
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

/**
 * 服务卡。`showUnit`（「显示单位」）关掉时**只不缀单位**，值照旧 —— 单位是点表里的用户数据，
 * 不是文案（与 web `service.state.ts` 的 `unit: showUnit ? row.unit : ''` 同口径）。
 *
 * `half`（这张卡是半宽档）只影响最后那行角标里**「采于 …」那半句**，见 [serviceShowsRecordedAt]。
 */
@Composable
private fun ServiceView(data: Map<String, Any?>?, showUnit: Boolean, half: Boolean) {
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
                    text = rowValue(row, showUnit),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        // 时间角标 + 失败标识：值是最后一次**成功**的，失败与值可同存（不同设备卡）。
        // 半宽卡只省掉「采于 …」那半句 —— 失败原因照旧（见 [serviceShowsRecordedAt]）。
        val caption = buildList {
            if (serviceShowsRecordedAt(recordedAt, half)) {
                recordedAt?.let { add("采于 " + friendlyTime(it)) }
            }
            error?.let { add("⚠ $it") }
        }
        if (caption.isNotEmpty()) {
            Text(
                text = caption.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = if (error != null)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun rowValue(row: MobileRenderFormat.ServiceRow, showUnit: Boolean): String {
    if (!row.hasValue) return "-"
    if (row.bit) return if (row.value == true) "开" else "关"
    val text = row.value?.toString() ?: return "-"
    return if (showUnit && row.unit.isNotBlank()) "$text ${row.unit}" else text
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