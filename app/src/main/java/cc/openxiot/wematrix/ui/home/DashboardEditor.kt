package cc.openxiot.wematrix.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.openxiot.wematrix.data.api.MobileCatalog
import cc.openxiot.wematrix.data.api.MobileDashboardWidget
import cc.openxiot.wematrix.data.api.ModbusService
import cc.openxiot.wematrix.data.api.ModbusServiceFunction
import cc.openxiot.wematrix.data.repository.ProductSpecRepository
import cc.openxiot.wematrix.ui.components.CustomRangeDialog
import cc.openxiot.wematrix.ui.components.FilterDropdown
import cc.openxiot.wematrix.ui.components.FilterOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * 看板编辑器：加卡弹层（picker）+ 每张卡的编辑底部弹层。
 *
 * 编辑的是**草稿**（VM 的 `draft`），不入库；底部「保存」确认后整体回调 VM 提交这张卡的
 * title / size / config。级联候选读 [MobileCatalog]（服务 / 设备 / 方法 / 字段），设备属性名
 * 去产品规格里查（[ProductSpecRepository]）—— 都只在进编辑态取过一次，这里不再出网。
 *
 * config 键名与 web / 后端逐字同构（见 [DashboardTypes]），这里的控件只是帮你把值填进
 * `Map<String, Any?>`，不改形状。
 */

// ===== 加卡弹层 =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardPickerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    if (!visible) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = "添加卡片",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            DashboardTypes.ALL.forEach { type ->
                Column(Modifier.padding(horizontal = 16.dp)) {
                    PickerRow(
                        icon = iconOf(type),
                        title = DashboardTypes.defaultTitle(type),
                        subtitle = subtitleOf(type)
                    ) { onAdd(type) }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun iconOf(type: String): ImageVector = when (type) {
    DashboardTypes.LINE -> Icons.Outlined.BarChart
    DashboardTypes.DISTRIBUTION -> Icons.Outlined.PieChart
    DashboardTypes.DEVICE -> Icons.Outlined.Router
    DashboardTypes.SERVICE -> Icons.Outlined.Devices
    else -> Icons.Outlined.StickyNote2
}

private fun subtitleOf(type: String): String = when (type) {
    DashboardTypes.STAT -> "一个主数字：设备 / 服务 / 告警 / 故障"
    DashboardTypes.LINE -> "整点告警曲线，或一条服务字段曲线"
    DashboardTypes.DISTRIBUTION -> "按设备 / 服务 / 告警 / 故障分布"
    DashboardTypes.DEVICE -> "某台设备的某个属性"
    else -> "某个方法的一组字段"
}

@Composable
private fun PickerRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ===== 每张卡的编辑弹层 =====

/** 编辑一张卡。`config` 在弹层内就地改，确认时整体回传（title / size / config）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardCardEditorSheet(
    widget: MobileDashboardWidget,
    catalog: MobileCatalog?,
    productSpec: ProductSpecRepository,
    onCommit: (title: String, size: String, config: Map<String, Any?>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(widget.id) { mutableStateOf(widget.title ?: "") }
    var size by remember(widget.id) { mutableStateOf(widget.size ?: DashboardTypes.defaultSize(widget.type)) }
    var config by remember(widget.id) { mutableStateOf(widget.config) }

    val canSave = DashboardTypes.canCommit(widget.type, config)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "编辑 · ${DashboardTypes.defaultTitle(widget.type)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("卡片标题") },
                placeholder = { Text("留空则用默认名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SizeSelector(type = widget.type, size = size, onSizeChange = { size = it })

            when (widget.type) {
                DashboardTypes.STAT -> StatConfig(config) { config = it }
                DashboardTypes.LINE -> LineConfig(config, catalog) { config = it }
                DashboardTypes.DISTRIBUTION -> DistributionConfig(config) { config = it }
                DashboardTypes.DEVICE -> DeviceConfig(config, catalog, productSpec) { config = it }
                DashboardTypes.SERVICE -> ServiceConfig(config, catalog) { config = it }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("删除")
                }
                OutlinedButton(
                    onClick = { onCommit(title.trim(), size, config) },
                    enabled = canSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Done, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    // 「确认」而不是「保存」：保存这张卡 ≠ 保存布局（页顶那颗按钮才是落库的），
                    // 两个「保存」并排会让人以为点了它就存进去了。web 的编辑器也是「确认」。
                    Text("确认")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SizeSelector(type: String?, size: String, onSizeChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "尺寸",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        FilterChip(selected = size == DashboardTypes.SIZE_FULL, onClick = { onSizeChange(DashboardTypes.SIZE_FULL) }, label = { Text("整宽") })
        FilterChip(
            selected = size == DashboardTypes.SIZE_HALF,
            onClick = { onSizeChange(DashboardTypes.SIZE_HALF) },
            label = { Text("半宽") },
            enabled = DashboardTypes.sizeAllowed(type, DashboardTypes.SIZE_HALF)
        )
    }
}

// ===== 各类型 config 区 =====

@Composable
private fun StatConfig(config: Map<String, Any?>, onChange: (Map<String, Any?>) -> Unit) {
    SectionLabel("统计指标")
    FilterDropdown(
        label = "指标",
        value = config["metric"] as? String,
        options = DashboardTypes.STAT_METRICS.map { FilterOption(it, metricLabel(it)) },
        onSelect = { m -> if (m != null) onChange(HashMap(config).apply { this["metric"] = m }) },
        placeholder = "请选择"
    )
    if (DashboardTypes.statNeedsWindow(config["metric"] as? String)) {
        WindowEditor(config["window"]) { w -> onChange(HashMap(config).apply { this["window"] = w }) }
    }
}

private fun metricLabel(metric: String): String = when (metric) {
    "devices.total" -> "设备总数"
    "devices.online" -> "设备在线数"
    "services.total" -> "服务总数"
    "alarms.today" -> "今日告警"
    "alarms.window" -> "窗口内告警"
    "failures.total" -> "窗口内采集失败数"
    else -> metric
}

@Composable
private fun DistributionConfig(config: Map<String, Any?>, onChange: (Map<String, Any?>) -> Unit) {
    SectionLabel("分布维度")
    FilterDropdown(
        label = "维度",
        value = config["dimension"] as? String,
        options = DashboardTypes.DIMENSIONS.map { FilterOption(it, dimensionLabel(it)) },
        onSelect = { d -> if (d != null) onChange(HashMap(config).apply { this["dimension"] = d }) },
        placeholder = "请选择"
    )
    if (DashboardTypes.distributionNeedsWindow(config["dimension"] as? String)) {
        WindowEditor(config["window"]) { w -> onChange(HashMap(config).apply { this["window"] = w }) }
    }
}

private fun dimensionLabel(d: String): String = when (d) {
    "deviceType" -> "设备类型"
    "serviceType" -> "服务类型"
    "alarmType" -> "告警类型"
    "failureType" -> "采集失败类型"
    else -> d
}

@Composable
private fun LineConfig(config: Map<String, Any?>, catalog: MobileCatalog?, onChange: (Map<String, Any?>) -> Unit) {
    val source = config["source"] as? String ?: "alarmCount"
    SectionLabel("数据源")
    FilterDropdown(
        label = "源",
        value = source,
        options = listOf(
            FilterOption("alarmCount", "整点告警曲线"),
            FilterOption("serviceField", "一条服务字段曲线")
        ),
        // 换源**只改 source**，不清「服务 / 方法 / 字段」那一组 —— 与 web `setLineSource`
        // 同口径（那条注释写了理由：它们只对 serviceField 那一支有意义，换到别处留着不会生效，
        // 换回来时还能接着上次的选择）。后端 `validateLine` 只校验它认得的那几个键，多余键不报错。
        onSelect = { s -> if (s != null) onChange(DashboardTypes.configWith(config, "source", s)) }
    )
    WindowEditor(config["window"]) { w -> onChange(HashMap(config).apply { this["window"] = w }) }

    if (source == "serviceField") {
        ServiceCascade(config, catalog, single = true, activeKey = "field") { k, v -> onChange(DashboardTypes.configWith(config, k, v)) }
    }
}

@Composable
private fun ServiceConfig(config: Map<String, Any?>, catalog: MobileCatalog?, onChange: (Map<String, Any?>) -> Unit) {
    SectionLabel("服务 · 方法 · 字段")
    // fields 是**多选**：换服务/方法会清掉旧字段（SIID/字段变了就不该残留旧值）
    ServiceCascade(config, catalog, single = false, activeKey = "fields") { k, v -> onChange(DashboardTypes.configWith(config, k, v)) }
}

/**
 * 服务 → 方法 → 字段 三级级联，line（只读字段单条）/ service（多选字段）共用。
 *
 * 服务与方法都选对后字段区才出现；换服务 / 换方法都把选中的字段**删掉**（旧服务/方法的字段
 * 在新上下文里没有意义）—— 是删键而不是写个空值，与 web 的 `undefined` 同口径
 * （见 [DashboardTypes.configWith]）。级联选出来的键经 [set] 写回 config：
 * `serviceId`（十六进制）、`functionIndex`（1 起）、[activeKey]（"field" 单条 / "fields" 列表）。
 */
@Composable
private fun ServiceCascade(
    config: Map<String, Any?>,
    catalog: MobileCatalog?,
    single: Boolean,
    activeKey: String,
    set: (String, Any?) -> Unit
) {
    val services = readServices(catalog)
    FilterDropdown(
        label = "服务",
        value = config["serviceId"] as? String,
        options = services.map { FilterOption(it.id.orEmpty(), it.name ?: it.id ?: "未命名服务") },
        onSelect = { id ->
            if (id != null) {
                // 默认选中第一个**可读**方法，少一次点击（写方法 / 无应答的方法不在候选里）。
                // **不能硬写 1**：方法序号由服务自己定，未必从 1 起 —— 写死 1 时方法框会选不中
                // 候选项里的任何一项，字段区也就永远不出现。真一个可读方法都没有时删键
                // （下拉显示「请选择方法」，「确认」按 [DashboardTypes.canCommit] 灰着）。
                val first = readFunctions(services.firstOrNull { it.id == id }).firstOrNull()?.index
                set("serviceId", id)
                set("functionIndex", first)
                set(activeKey, null) // 下游字段删键（换了服务，旧字段名不再成立）
            }
        },
        placeholder = "请选择服务"
    )

    val service = services.firstOrNull { it.id == config["serviceId"] }
    val functions = readFunctions(service)
    val functionIndex = (config["functionIndex"] as? Number)?.toInt()
    FilterDropdown(
        label = "方法",
        value = functionIndex,
        options = functions.map { FilterOption(it.index ?: -1, it.name ?: "方法 ${it.index}") },
        onSelect = { idx ->
            if (idx != null) {
                set("functionIndex", idx)
                set(activeKey, null) // 同上：换方法，旧字段名不再成立
            }
        },
        placeholder = if (service == null) "先选服务" else "请选择方法"
    )

    val function = functions.firstOrNull { it.index == functionIndex }
    val fields = function?.response ?: emptyList()
    if (single) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.forEach { f ->
                val name = f.field ?: return@forEach
                val selected = config["field"] == name
                FilterChip(selected = selected, onClick = { set("field", name) }, label = { Text(name) })
            }
        }
    } else {
        val selected = ((config[activeKey] as? List<*>)?.mapNotNull { it as? String })?.toSet()
            ?: emptySet()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.forEach { f ->
                val name = f.field ?: return@forEach
                FilterChip(
                    selected = name in selected,
                    onClick = { set(activeKey, selected.toMutableSet().apply { if (!add(name)) remove(name) }.toList()) },
                    label = { Text(name) }
                )
            }
        }
    }
}

@Composable
private fun DeviceConfig(
    config: Map<String, Any?>,
    catalog: MobileCatalog?,
    productSpec: ProductSpecRepository,
    onChange: (Map<String, Any?>) -> Unit
) {
    val devices = catalog?.devices ?: emptyList()
    val scope = rememberCoroutineScope()
    SectionLabel("设备 · 属性")
    FilterDropdown(
        label = "设备",
        value = config["did"] as? String,
        options = devices.map { FilterOption(it.did.orEmpty(), deviceLabel(it.did, it.online)) },
        onSelect = { did ->
            if (did != null) {
                val type = devices.firstOrNull { it.did == did }?.type
                type?.let { t -> scope.launch { productSpec.load(t) } } // 到了规格，下面的属性下拉跟着出
                onChange(HashMap(config).apply { this["did"] = did; remove("pid") })
            }
        },
        placeholder = "请选择设备"
    )

    val selectedType = devices.firstOrNull { it.did == config["did"] }?.type
    val props = productSpec.propertiesOf(selectedType)
    FilterDropdown(
        label = "属性",
        value = config["pid"] as? String,
        options = props.map { p ->
            val pid = "${config["did"]}.${p.siid}.${p.piid}"
            FilterOption(pid, p.name.ifBlank { "SIID ${p.siid}.PIID ${p.piid}" } + propUnit(p.unit))
        },
        onSelect = { pid -> if (pid != null) onChange(HashMap(config).apply { this["pid"] = pid }) },
        placeholder = when {
            config["did"] == null -> "先选设备"
            props.isEmpty() -> "该型号暂无属性"
            else -> "请选择属性"
        }
    )
}

private fun deviceLabel(did: String?, online: Boolean?): String {
    val status = if (online == true) "在线" else if (online == false) "离线" else ""
    return listOfNotNull(did, status.takeIf { it.isNotBlank() }).joinToString(" ")
}

private fun propUnit(unit: String): String = if (unit.isBlank()) "" else " ($unit)"

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

// ===== 时间窗口 =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowEditor(window: Any?, onWindowChange: (Any?) -> Unit) {
    SectionLabel("时间窗口")
    val current = window as? Map<*, *>
    val kind = current?.get("kind") as? String ?: "last"
    val hours = (current?.get("hours") as? Number)?.toInt() ?: 24
    var showRangeDialog by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = kind == "last",
            onClick = { onWindowChange(mapOf("kind" to "last", "hours" to hours)) },
            label = { Text("最近") }
        )
        FilterChip(
            selected = kind == "range",
            onClick = { showRangeDialog = true },
            label = { Text("自定义") }
        )
    }

    if (kind == "last") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardTypes.WINDOW_HOURS.forEach { h ->
                FilterChip(
                    selected = hours == h,
                    onClick = { onWindowChange(mapOf("kind" to "last", "hours" to h)) },
                    label = { Text(hourLabel(h)) }
                )
            }
        }
    } else {
        val from = (current?.get("from") as? Number)?.toLong()
        val to = (current?.get("to") as? Number)?.toLong()
        OutlinedButton(onClick = { showRangeDialog = true }) {
            Text(if (from != null && to != null) dayLabel(from) + " ~ " + dayLabel(to) else "选择起止日期")
        }
    }

    if (showRangeDialog) {
        CustomRangeDialog(
            initialFrom = (current?.get("from") as? Number)?.toLong(),
            initialTo = (current?.get("to") as? Number)?.toLong(),
            onDismiss = { showRangeDialog = false },
            onConfirm = { f, t ->
                showRangeDialog = false
                onWindowChange(mapOf("kind" to "range", "from" to f, "to" to t))
            }
        )
    }
}

private fun hourLabel(h: Int): String = when {
    h == 168 -> "7 天"
    h % 24 == 0 -> "${h / 24} 天"
    else -> "$h 小时"
}

private fun dayLabel(ms: Long): String =
    DateTimeFormatter.ofPattern("MM-dd")
        .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

// ===== 服务级联候选 =====

/** 有可读字段的服务（至少一个读方法）。写方法没有可选字段，排除。 */
private fun readServices(catalog: MobileCatalog?): List<ModbusService> =
    catalog?.services?.filter { it.functions.any { f -> f.response.isNotEmpty() } } ?: emptyList()

private fun readFunctions(service: ModbusService?): List<ModbusServiceFunction> =
    service?.functions?.filter { it.response.isNotEmpty() } ?: emptyList()