package cc.openxiot.matrix.ui.home

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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.api.MobileCatalog
import cc.openxiot.matrix.data.api.MobileDashboardWidget
import cc.openxiot.matrix.data.api.ModbusService
import cc.openxiot.matrix.data.api.ModbusServiceFunction
import cc.openxiot.matrix.data.repository.ProductSpecRepository
import cc.openxiot.matrix.ui.components.CustomRangeDialog
import cc.openxiot.matrix.ui.components.FilterDropdown
import cc.openxiot.matrix.ui.components.FilterOption
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
                text = stringResource(R.string.home_add_card_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            DashboardTypes.ALL.forEach { type ->
                Column(Modifier.padding(horizontal = 16.dp)) {
                    PickerRow(
                        icon = iconOf(type),
                        title = stringResource(DashboardTypes.defaultTitleRes(type)),
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

@Composable
private fun subtitleOf(type: String): String = when (type) {
    DashboardTypes.STAT -> stringResource(R.string.home_card_kind_stat)
    DashboardTypes.LINE -> stringResource(R.string.home_card_kind_line)
    DashboardTypes.DISTRIBUTION -> stringResource(R.string.home_card_kind_distribution)
    DashboardTypes.DEVICE -> stringResource(R.string.home_card_kind_device)
    else -> stringResource(R.string.home_card_kind_service)
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
                text = stringResource(
                    R.string.home_edit_card_title,
                    stringResource(DashboardTypes.defaultTitleRes(widget.type))
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.home_card_title_label)) },
                placeholder = { Text(stringResource(R.string.home_card_title_placeholder)) },
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
                    Text(stringResource(R.string.common_delete))
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
                    Text(stringResource(R.string.common_confirm))
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
            text = stringResource(R.string.home_size_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        FilterChip(selected = size == DashboardTypes.SIZE_FULL, onClick = { onSizeChange(DashboardTypes.SIZE_FULL) }, label = { Text(stringResource(R.string.home_size_full)) })
        FilterChip(
            selected = size == DashboardTypes.SIZE_HALF,
            onClick = { onSizeChange(DashboardTypes.SIZE_HALF) },
            label = { Text(stringResource(R.string.home_size_half)) },
            enabled = DashboardTypes.sizeAllowed(type, DashboardTypes.SIZE_HALF)
        )
    }
}

// ===== 各类型 config 区 =====

@Composable
private fun StatConfig(config: Map<String, Any?>, onChange: (Map<String, Any?>) -> Unit) {
    SectionLabel(stringResource(R.string.home_section_metric))
    FilterDropdown(
        label = stringResource(R.string.home_metric_label),
        value = config["metric"] as? String,
        options = DashboardTypes.STAT_METRICS.map { FilterOption(it, metricLabel(it)) },
        onSelect = { m -> if (m != null) onChange(HashMap(config).apply { this["metric"] = m }) },
        placeholder = stringResource(R.string.common_please_select)
    )
    if (DashboardTypes.statNeedsWindow(config["metric"] as? String)) {
        WindowEditor(config["window"]) { w -> onChange(HashMap(config).apply { this["window"] = w }) }
    }
}

/** 指标名。`else -> metric` 是回落成原始键名（见上），故返回 String 而非资源 id。 */
@Composable
private fun metricLabel(metric: String): String = when (metric) {
    "devices.total" -> stringResource(R.string.home_metric_devices_total)
    "devices.online" -> stringResource(R.string.home_metric_devices_online)
    "services.total" -> stringResource(R.string.home_metric_services_total)
    "alarms.today" -> stringResource(R.string.home_metric_alarms_today)
    "alarms.window" -> stringResource(R.string.home_metric_alarms_window)
    "failures.total" -> stringResource(R.string.home_metric_failures_total)
    else -> metric
}

@Composable
private fun DistributionConfig(config: Map<String, Any?>, onChange: (Map<String, Any?>) -> Unit) {
    SectionLabel(stringResource(R.string.home_section_dimension))
    FilterDropdown(
        label = stringResource(R.string.home_dimension_label),
        value = config["dimension"] as? String,
        options = DashboardTypes.DIMENSIONS.map { FilterOption(it, dimensionLabel(it)) },
        onSelect = { d -> if (d != null) onChange(HashMap(config).apply { this["dimension"] = d }) },
        placeholder = stringResource(R.string.common_please_select)
    )
    // 显示层折算（[truncateSlices]）：后端全量下发，改这个不必重新取数
    NumberField(
        label = stringResource(R.string.home_slice_limit_label),
        value = DashboardTypes.configInt(config, "limit"),
        range = 1..100,
        placeholder = stringResource(R.string.common_unlimited)
    ) { n -> onChange(DashboardTypes.configWith(config, "limit", n)) }
    if (DashboardTypes.distributionNeedsWindow(config["dimension"] as? String)) {
        WindowEditor(config["window"]) { w -> onChange(HashMap(config).apply { this["window"] = w }) }
    }
}

/** 分布维度名。同 [metricLabel]：`else -> d` 是回落成原始键名。 */
@Composable
private fun dimensionLabel(d: String): String = when (d) {
    "deviceType" -> stringResource(R.string.home_dimension_device_type)
    "serviceType" -> stringResource(R.string.home_dimension_service_type)
    "alarmType" -> stringResource(R.string.home_dimension_alarm_type)
    "failureType" -> stringResource(R.string.home_dimension_failure_type)
    else -> d
}

@Composable
private fun LineConfig(config: Map<String, Any?>, catalog: MobileCatalog?, onChange: (Map<String, Any?>) -> Unit) {
    val source = config["source"] as? String ?: "alarmCount"
    SectionLabel(stringResource(R.string.home_section_source))
    FilterDropdown(
        label = stringResource(R.string.home_source_label),
        value = source,
        options = listOf(
            FilterOption("alarmCount", stringResource(R.string.home_source_alarm_curve)),
            FilterOption("serviceField", stringResource(R.string.home_source_service_field))
        ),
        // 换源**只改 source**，不清「服务 / 方法 / 字段」那一组 —— 与 web `setLineSource`
        // 同口径（那条注释写了理由：它们只对 serviceField 那一支有意义，换到别处留着不会生效，
        // 换回来时还能接着上次的选择）。后端 `validateLine` 只校验它认得的那几个键，多余键不报错。
        onSelect = { s -> if (s != null) onChange(DashboardTypes.configWith(config, "source", s)) }
    )
    WindowEditor(config["window"]) { w -> onChange(HashMap(config).apply { this["window"] = w }) }

    if (source == "serviceField") {
        ServiceCascade(config, catalog, single = true, activeKey = "field", onChange = onChange)
        // 竖线说的是「这个方法的采集什么时候失败过」，与读数本身是两件事（web 同款文案）
        SwitchRow(
            label = stringResource(R.string.home_show_failure_shadow),
            checked = DashboardTypes.configBool(config, "showFailureShadow", default = true),
            onCheckedChange = { onChange(DashboardTypes.configWith(config, "showFailureShadow", it)) }
        )
        // 降采样只对「服务字段」这一支存在：告警条数曲线是后端按整点**零填充**的，
        // 每个整点都在数组里，没有「点太多」这回事（取数也不读这个键）。所以它跟竖线一样
        // 只在这一支里出现 —— 摆一个改了没反应的控件比不摆更糟（web 那条注释的原话）。
        NumberField(
            label = stringResource(R.string.home_max_points_label),
            value = DashboardTypes.configInt(config, "maxPoints"),
            range = 1..2000,
            placeholder = stringResource(R.string.common_unlimited)
        ) { n -> onChange(DashboardTypes.configWith(config, "maxPoints", n)) }
    }
}

@Composable
private fun ServiceConfig(config: Map<String, Any?>, catalog: MobileCatalog?, onChange: (Map<String, Any?>) -> Unit) {
    SectionLabel(stringResource(R.string.home_section_service_field))
    // fields 是**多选**：换服务/方法会清掉旧字段（SIID/字段变了就不该残留旧值）
    ServiceCascade(config, catalog, single = false, activeKey = "fields", onChange = onChange)
    // 单位是点表里的用户数据，不是文案 —— 关掉只是不缀它
    SwitchRow(
        label = stringResource(R.string.home_show_unit),
        checked = DashboardTypes.configBool(config, "showUnit", default = true),
        onCheckedChange = { onChange(DashboardTypes.configWith(config, "showUnit", it)) }
    )
    // 设备卡**不给**这个开关：web 那边的设备单位是本地产品规格里查的（`property.unit`），
    // 后端 payload 只有 pid/type/value/error、根本没有 unit 这个键，而只读卡片壳也拿不到
    // 规格仓库 —— 摆一个改了没反应的开关比不摆更糟。
}

/**
 * 服务 → 方法 → 字段 三级级联，line（只读字段单条）/ service（多选字段）共用。
 *
 * 服务与方法都选对后字段区才出现；换服务 / 换方法都把选中的字段**删掉**（旧服务/方法的字段
 * 在新上下文里没有意义）—— 是删键而不是写个空值，与 web 的 `undefined` 同口径
 * （见 [DashboardTypes.configWith]）。级联选出来的键经 [edit] 写回 config：
 * `serviceId`（十六进制）、`functionIndex`（1 起）、[activeKey]（"field" 单条 / "fields" 列表）。
 *
 * **[edit] 而不是连着调三次 `onChange`，是这个控件的要害**：这里每次交互要同时改几个键
 * （换服务 = 服务 + 方法 + 清字段），而 `config` 是**组合时那份值**（普通参数，不是 State）——
 * 连写三次的话，三次都拿着同一份旧 map 去算，谁最后谁赢，净效果等于**一个字都没改**
 * （选服务 → 服务框弹回「请选择服务」，看着就是「选不中」）。所以：**一次交互只 onChange 一次**，
 * 几个键在 [edit] 里折进同一份 map。
 */
@Composable
private fun ServiceCascade(
    config: Map<String, Any?>,
    catalog: MobileCatalog?,
    single: Boolean,
    activeKey: String,
    onChange: (Map<String, Any?>) -> Unit
) {
    fun edit(vararg pairs: Pair<String, Any?>) {
        onChange(DashboardTypes.configWith(config, *pairs))
    }

    val services = readServices(catalog)
    FilterDropdown(
        label = stringResource(R.string.common_service_label),
        value = config["serviceId"] as? String,
        options = services.map { FilterOption(it.id.orEmpty(), it.name ?: it.id ?: stringResource(R.string.home_service_unnamed)) },
        onSelect = { id ->
            if (id != null) {
                // 默认选中第一个**可读**方法，少一次点击（写方法 / 无应答的方法不在候选里）。
                // **不能硬写 1**：方法序号由服务自己定，未必从 1 起 —— 写死 1 时方法框会选不中
                // 候选项里的任何一项，字段区也就永远不出现。真一个可读方法都没有时删键
                // （下拉显示「请选择方法」，「确认」按 [DashboardTypes.canCommit] 灰着）。
                val first = readFunctions(services.firstOrNull { it.id == id }).firstOrNull()?.index
                edit("serviceId" to id, "functionIndex" to first, activeKey to null)
            }
        },
        placeholder = stringResource(R.string.home_service_placeholder)
    )

    val service = services.firstOrNull { it.id == config["serviceId"] }
    val functions = readFunctions(service)
    val functionIndex = (config["functionIndex"] as? Number)?.toInt()
    FilterDropdown(
        label = stringResource(R.string.common_function_label),
        value = functionIndex,
        options = functions.map { FilterOption(it.index ?: -1, it.name ?: stringResource(R.string.home_function_fallback, it.index ?: -1)) },
        onSelect = { idx ->
            if (idx != null) {
                edit("functionIndex" to idx, activeKey to null) // 换方法，旧字段名不再成立
            }
        },
        placeholder = stringResource(if (service == null) R.string.home_pick_service_first else R.string.home_function_placeholder)
    )

    val function = functions.firstOrNull { it.index == functionIndex }
    val fields = function?.response?.fields.orEmpty()
    if (single) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.forEach { f ->
                val name = f.field ?: return@forEach
                FilterChip(
                    selected = config[activeKey] == name,
                    onClick = { edit(activeKey to name) },
                    label = { Text(name) }
                )
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
                    onClick = { edit(activeKey to selected.toMutableSet().apply { if (!add(name)) remove(name) }.toList()) },
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
    SectionLabel(stringResource(R.string.home_section_device_prop))
    FilterDropdown(
        label = stringResource(R.string.common_device_label),
        value = config["did"] as? String,
        options = devices.map { FilterOption(it.did.orEmpty(), deviceLabel(it.did, it.online)) },
        onSelect = { did ->
            if (did != null) {
                val type = devices.firstOrNull { it.did == did }?.type
                type?.let { t -> scope.launch { productSpec.load(t) } } // 到了规格，下面的属性下拉跟着出
                onChange(HashMap(config).apply { this["did"] = did; remove("pid") })
            }
        },
        placeholder = stringResource(R.string.home_device_placeholder)
    )

    val selectedType = devices.firstOrNull { it.did == config["did"] }?.type
    val props = productSpec.propertiesOf(selectedType)
    FilterDropdown(
        label = stringResource(R.string.home_prop_label),
        value = config["pid"] as? String,
        options = props.map { p ->
            val pid = "${config["did"]}.${p.siid}.${p.piid}"
            FilterOption(pid, p.name.ifBlank { "SIID ${p.siid}.PIID ${p.piid}" } + propUnit(p.unit))
        },
        onSelect = { pid -> if (pid != null) onChange(HashMap(config).apply { this["pid"] = pid }) },
        placeholder = stringResource(
            when {
                config["did"] == null -> R.string.home_pick_device_first
                props.isEmpty() -> R.string.home_prop_none
                else -> R.string.home_prop_placeholder
            }
        )
    )
}

/** `型号 在线`。三态里那一态是文案，故整个函数改成 `@Composable`（只在 `map` 里被调一次）。 */
@Composable
private fun deviceLabel(did: String?, online: Boolean?): String {
    val status = when (online) {
        true -> stringResource(R.string.common_online)
        false -> stringResource(R.string.common_offline)
        else -> ""
    }
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

// ===== 可选字段（开关 / 数字） =====

/** 一行开关：左边文案、右边 `Switch`。缺省值见各调用处的 [DashboardTypes.configBool]。 */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 可选数字项（最大点数 / 显示片数）：**留空 = 不限制（删键）**，填了必须落在 [range] 里。
 *
 * 输入只收数字。**范围外的值不写进 config**（原地标红提示，config 里当没设）——
 * 这一条比 web 严：web 的 `nz-input-number` 只设下限、超上限照写，于是能存出一份后端必拒的
 * config（`config.maxPoints must be between 1 and 2000`），要等保存整份布局时才报错。
 * 移动端保存是一次 PUT 整屏，宁可当场标红。
 */
@Composable
private fun NumberField(
    label: String,
    value: Int?,
    range: IntRange,
    placeholder: String,
    onValueChange: (Int?) -> Unit
) {
    var text by remember(label) { mutableStateOf(value?.toString() ?: "") }
    val parsed = text.toIntOrNull()
    val invalid = text.isNotEmpty() && (parsed == null || parsed !in range)
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(4)
            text = digits
            val n = digits.toIntOrNull()
            onValueChange(if (n != null && n in range) n else null)
        },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        isError = invalid,
        supportingText = if (invalid) {
            { Text(stringResource(R.string.home_number_range_hint, range.first, range.last)) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

// ===== 时间窗口 =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowEditor(window: Any?, onWindowChange: (Any?) -> Unit) {
    SectionLabel(stringResource(R.string.home_section_window))
    val current = window as? Map<*, *>
    val kind = current?.get("kind") as? String ?: "last"
    val hours = (current?.get("hours") as? Number)?.toInt() ?: 24
    var showRangeDialog by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = kind == "last",
            onClick = { onWindowChange(mapOf("kind" to "last", "hours" to hours)) },
            label = { Text(stringResource(R.string.home_window_last)) }
        )
        FilterChip(
            selected = kind == "range",
            onClick = { showRangeDialog = true },
            label = { Text(stringResource(R.string.home_window_custom)) }
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
            Text(if (from != null && to != null) dayLabel(from) + " ~ " + dayLabel(to) else stringResource(R.string.home_pick_dates))
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

/**
 * 窗口档位的显示名。
 *
 * 原先 `h == 168 -> "7 天"` 与 `h % 24 == 0 -> "${h / 24} 天"` 是两个分支，但 168 也是 24 的
 * 倍数、168/24 正好是 7，两条输出**逐字相同** —— 合掉，行为不变。
 *
 * 数量传两次：一次选档位（英文 1 day / 2 days），一次填 `%1$d`。
 */
@Composable
private fun hourLabel(h: Int): String = when {
    h % 24 == 0 -> pluralStringResource(R.plurals.home_window_days, h / 24, h / 24)
    else -> pluralStringResource(R.plurals.home_window_hours, h, h)
}

private fun dayLabel(ms: Long): String =
    DateTimeFormatter.ofPattern("MM-dd")
        .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

// ===== 服务级联候选 =====

/** 有可读字段的服务（至少一个读方法）。写方法没有可选字段，排除。 */
private fun readServices(catalog: MobileCatalog?): List<ModbusService> =
    catalog?.services?.filter { it.functions.any { f -> f.response?.fields.isNotEmptyOrNull() } } ?: emptyList()

private fun readFunctions(service: ModbusService?): List<ModbusServiceFunction> =
    service?.functions?.filter { it.response?.fields.isNotEmptyOrNull() } ?: emptyList()

/** null（写方法）或空表都当「没可读字段」。 */
private fun List<*>?.isNotEmptyOrNull(): Boolean = !this.isNullOrEmpty()