package cc.openxiot.wematrix.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.data.api.ModbusHistoryFailure
import cc.openxiot.wematrix.ui.components.CustomRangeDialog
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.FilterDropdown
import cc.openxiot.wematrix.ui.components.FilterOption
import cc.openxiot.wematrix.ui.components.FilterRow
import cc.openxiot.wematrix.ui.components.InfoChip
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import cc.openxiot.wematrix.ui.components.RangePresetChips
import cc.openxiot.wematrix.ui.modbus.RangePreset
import cc.openxiot.wematrix.ui.modbus.epochDate
import cc.openxiot.wematrix.ui.modbus.failureLabel
import cc.openxiot.wematrix.ui.modbus.formatEpochMillis
import cc.openxiot.wematrix.ui.theme.Gray500
import cc.openxiot.wematrix.ui.theme.Green
import cc.openxiot.wematrix.ui.theme.Red

/**
 * 单服务历史页：一个服务的逐字段采集历史，对齐 webapp-matrix 的
 * `pages/main/device/services/service/history/`。
 *
 * 同一份窗口数据有两种看法：
 * - **表格**：一行一条采样（时间 / 方法 / 字段 / 值 / 单位 / 备注）。采集是「值变了才记一条」，
 *   只有长表能把不同字段各自的时刻如实摊开 —— 宽表的行是齐的，但大部分格子会是空的。
 * - **曲线图**：一个字段一张小图、竖向排列，只有最下面那张画时间刻度；采集失败在图上打一条
 *   竖虚线，明细见页尾的「采集异常」。
 *
 * web 侧「字段」是一个多选框，这里改成一个弹窗（手机上的多选框一展开就吃掉半屏）：
 * 勾几个画几张，勾选顺序无所谓 —— 图按字段定义顺序排，换一批不会让图跳来跳去。
 *
 * 一条硬规矩：**来自服务端与用户的文本一律原样显示、永不翻译** —— 字段名、单位、取值表的描述、
 * 失败消息、服务名都在其列。页面只把后端**枚举名**（失败类型）换成中文词。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceHistoryScreen(
    rootId: String,
    serviceId: String,
    onBack: () -> Unit,
    viewModel: ServiceHistoryViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    var showCustomPicker by remember { mutableStateOf(false) }
    var showFieldPicker by remember { mutableStateOf(false) }

    LaunchedEffect(rootId, serviceId) { viewModel.load(rootId, serviceId) }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text(
                        // 服务名是服务端数据、原样显示；取不到定义时退回「历史」
                        text = state.service?.name ?: "历史",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 4.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val serviceError = state.serviceError
            when {
                state.isLoadingService && state.service == null -> LoadingIndicator()

                serviceError != null && state.service == null -> EmptyState(
                    message = serviceError,
                    modifier = Modifier.clickable { viewModel.load(rootId, serviceId) }
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { HeaderCard(state) }

                    item {
                        FilterCard(
                            state = state,
                            onPresetChange = { preset ->
                                viewModel.setPreset(preset)
                                if (preset == RangePreset.CUSTOM) showCustomPicker = true
                            },
                            onEditCustomRange = { showCustomPicker = true },
                            onFunctionChange = viewModel::setFunction,
                            onViewChange = viewModel::setView,
                            onPickFields = { showFieldPicker = true }
                        )
                    }

                    // 取不到数的字段：表格没有挂错处，统一提示在这；曲线图各自挂在自己那张图上
                    if (state.view == HistoryView.TABLE) {
                        items(state.loadErrors) { message ->
                            NoticeCard(text = message, danger = true)
                        }
                    }
                    if (state.downsampled) {
                        item { NoticeCard(text = DOWNSAMPLED_HINT, danger = false) }
                    }

                    if (state.view == HistoryView.TABLE) {
                        val rows = state.rows
                        if (rows.isEmpty()) {
                            item { EmptyState("这段时间没有采集数据") }
                        } else {
                            item { SectionTitle("采集数据（${rows.size} 行）") }
                            items(rows, key = { it.key }) { row -> HistoryRowCard(row) }
                        }
                    } else {
                        val charts = state.charts
                        if (charts.isEmpty()) {
                            item {
                                EmptyState(
                                    message = if (state.numericFields.isEmpty()) {
                                        "这个服务的字段画不出曲线"
                                    } else {
                                        "没有勾选字段"
                                    }
                                )
                            }
                        } else {
                            if (state.failures?.items?.isNotEmpty() == true) {
                                item { NoticeCard(text = "竖线为采集失败时刻", danger = false) }
                            }
                            items(charts, key = { it.ref.key }) { chart -> ChartCard(chart, state) }
                        }
                    }

                    item { SectionTitle("采集异常") }
                    item { FailureSummary(state) }

                    val failures = state.failures
                    when {
                        failures == null && state.failuresError.isNotEmpty() ->
                            item { NoticeCard(text = state.failuresError, danger = true) }

                        failures == null || failures.items.isEmpty() ->
                            item { EmptyState("这段时间没有采集异常") }

                        else -> {
                            items(failures.items.size) { index ->
                                FailureCard(failures.items[index])
                            }
                            if (failures.truncated) {
                                item { NoticeCard(text = TRUNCATED_HINT, danger = false) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomPicker) {
        CustomRangeDialog(
            initialFrom = state.customFrom,
            initialTo = state.customTo,
            onDismiss = { showCustomPicker = false },
            onConfirm = { from, to ->
                showCustomPicker = false
                viewModel.setCustomRange(from, to)
            }
        )
    }

    if (showFieldPicker) {
        FieldPickerDialog(
            options = state.numericFields,
            selected = state.chartFields.toSet(),
            onDismiss = { showFieldPicker = false },
            onConfirm = { keys ->
                showFieldPicker = false
                viewModel.setChartFields(keys)
            }
        )
    }
}

/** 命中降采样时的说明 —— 与 web 同一句文案 */
private const val DOWNSAMPLED_HINT = "数据点较多，已按时间区间降采样（值为该段平均值，括号内为最小~最大值）"

/** 窗口内的失败多于上限时只列了最近的那部分 —— 与 web 同一句文案 */
private const val TRUNCATED_HINT = "异常记录超过上限，只列出最近的部分"

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
    )
}

/** 页头三项：依赖设备（did + 在线态）/ 方法数（已采到 / 可配轮询）/ 采集时间 */
@Composable
private fun HeaderCard(state: ServiceHistoryUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.service?.device?.did ?: "-",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                state.device?.let {
                    InfoChip(
                        text = if (it.online == true) "在线" else "离线",
                        color = if (it.online == true) Green else Gray500
                    )
                }
            }
            // 已采到数据的方法数 / 可配轮询的方法数：差得多说明有方法一直没采上
            KeyValue("方法数", state.methodCountText)
            KeyValue(
                label = "采集时间",
                value = state.lastRecordedAt?.let { formatEpochMillis(it) } ?: "-"
            )
        }
    }
}

@Composable
private fun FilterCard(
    state: ServiceHistoryUiState,
    onPresetChange: (RangePreset) -> Unit,
    onEditCustomRange: () -> Unit,
    onFunctionChange: (Int) -> Unit,
    onViewChange: (HistoryView) -> Unit,
    onPickFields: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RangePresetChips(preset = state.preset, onPresetChange = onPresetChange)

            if (state.preset == RangePreset.CUSTOM) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = customRangeText(state.customFrom, state.customTo),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onEditCustomRange) { Text("修改") }
                }
            }

            FilterRow {
                // 方法序号 0 = 全部方法：它与「不传」在接口上是同一件事，
                // 故这里让 null（不限）就代表全部，不必真塞一个 0 进下拉
                FilterDropdown(
                    label = "方法",
                    value = state.functionIndex.takeIf { it > 0 },
                    options = state.readFunctions.map { func ->
                        FilterOption(func.index ?: 0, state.functionLabel(func))
                    },
                    onSelect = { onFunctionChange(it ?: 0) },
                    placeholder = "全部方法"
                )
            }

            // 显示方式：两种取的字段集与 maxPoints 都不同，换一种要重取一次
            FilterRow {
                HistoryView.entries.forEach { view ->
                    FilterChip(
                        selected = state.view == view,
                        onClick = { onViewChange(view) },
                        label = {
                            Text(view.label, style = MaterialTheme.typography.labelMedium)
                        }
                    )
                }
                // 字段多选框收进弹窗：手机上一展开就吃掉半屏，而它只是个显示筛选
                if (state.view == HistoryView.CHART) {
                    TextButton(onClick = onPickFields) {
                        Text("字段 ${state.chartFields.size} / ${state.numericFields.size}")
                    }
                }
            }
        }
    }
}

/** 自定义档当前查的是哪一段（还没选过时为空） */
private fun customRangeText(from: Long?, to: Long?): String {
    if (from == null || to == null) return "尚未选择时间范围"
    return "${epochDate(from)} 至 ${epochDate(to)}"
}

/** 表格里的一行：时间 / 方法 · 字段 / 值 单位 /（保持） */
@Composable
private fun HistoryRowCard(row: HistoryRow) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.time,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                // 值没变、按 keep-alive 时限补记的一条：与「变了才记」区分开
                if (row.note.isNotEmpty()) {
                    InfoChip(row.note, Gray500)
                }
            }
            // 值 + 单位：数值收过浮点误差，单位是点表里的数据、原样缀上
            Text(
                text = if (row.unit.isNotEmpty()) "${row.value} ${row.unit}" else row.value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                // 方法名与字段名都是服务端数据，原样显示
                text = "#${row.functionIndex} ${row.functionName} · ${row.field}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 一张字段曲线图。
 *
 * 时间轴只有最下面那张画：几张图纵向排开、时间轴对齐，刻度重复画没意义 ——
 * 故把「是不是最后一张」交给位置判断，与 web 的 `showTimeAxis: i === refs.length - 1` 同口径。
 */
@Composable
private fun ChartCard(chart: HistoryChart, state: ServiceHistoryUiState) {
    val window = state.window
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chart.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "#${chart.ref.functionIndex} ${chart.ref.functionName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(4.dp))

            val range = chart.range
            when {
                chart.error.isNotEmpty() -> NoticeCard(chart.error, danger = true)

                range == null || window == null -> EmptyState(
                    message = "这段时间没有采集数据",
                    modifier = Modifier.height(120.dp)
                )

                else -> HistoryFieldChart(
                    series = buildHistorySeries(range, state.failureTimes[chart.ref.functionIndex].orEmpty()),
                    from = window.first,
                    to = window.second,
                    unit = chart.ref.unit,
                    showTimeAxis = chart.ref.key == state.charts.lastOrNull()?.ref?.key,
                    step = chart.ref.step,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 采集异常的类型汇总：按类型 + 按远端码两组标签。
 *
 * **只有从站异常应答那个码是 Modbus 异常码**（1/2/3/4…），依赖设备报错的远端码是 DTU 厂商
 * 自己的状态码，我们并不知道它的含义 —— 故按码那组只写「异常 N」，不猜着翻。
 * 这里用后端给的 summary（只统计本次返回的这批行），故要连着 truncated 一起读。
 */
@Composable
private fun FailureSummary(state: ServiceHistoryUiState) {
    val summary = state.failures?.summary ?: return
    if (summary.byType.isEmpty() && summary.byRemoteCode.isEmpty()) return
    FilterRow(modifier = Modifier.padding(horizontal = 16.dp)) {
        summary.byType.forEach { item ->
            InfoChip("${failureLabel(item.type, null)} × ${item.count}", Red)
        }
        summary.byRemoteCode.forEach { item ->
            InfoChip("异常 ${item.remoteCode} × ${item.count}", Gray500)
        }
    }
}

/**
 * 一条采集异常（时间 / 方法 / 类型 / 消息）。
 *
 * 记的是「这条错误**首次出现**的时刻」，同一条错误持续存在不会重复记 ——
 * 故这不是「每次失败一行」，时间列的读法要照着这个来。
 */
@Composable
private fun FailureCard(item: ModbusHistoryFailure) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatEpochMillis(item.at),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                InfoChip(failureLabel(item.type, item.remoteCode), Red)
            }
            KeyValue("方法", "#${item.functionIndex}")
            // 失败消息是服务端下发的原文，**原样显示、不翻译**
            Text(
                text = item.message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
            // 枚举名留在明面上（web 挂在标签的 title 上）：排查时要拿它去搜后端日志
            item.type?.takeIf { it.isNotEmpty() }?.let { type ->
                Text(
                    text = type,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 一条就地提示：取不到数（红）/ 降采样说明与截断提示（灰） */
@Composable
private fun NoticeCard(text: String, danger: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (danger) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (danger) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

/**
 * 曲线图的字段多选弹窗。
 *
 * 只列**画得出曲线的字段**（取值表命中的字符串字段没有数值，画不了），
 * 且不做数量上限 —— 勾了几张画几张（与 web 同口径）。
 */
@Composable
private fun FieldPickerDialog(
    options: List<FieldRef>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var picked by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("字段") },
        text = {
            if (options.isEmpty()) {
                Text("这个方法没有画得出曲线的字段")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(options, key = { it.key }) { ref ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    picked = if (picked.contains(ref.key)) {
                                        picked - ref.key
                                    } else {
                                        picked + ref.key
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = picked.contains(ref.key),
                                onCheckedChange = { checked ->
                                    picked = if (checked) picked + ref.key else picked - ref.key
                                }
                            )
                            Spacer(Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    // 字段名与单位都是服务端数据，原样显示
                                    text = if (ref.unit.isNotEmpty()) {
                                        "${ref.field} (${ref.unit})"
                                    } else {
                                        ref.field
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "#${ref.functionIndex} ${ref.functionName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(options.map { it.key }.filter { picked.contains(it) }) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
