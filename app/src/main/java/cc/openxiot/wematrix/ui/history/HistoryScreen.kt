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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import cc.openxiot.wematrix.ui.components.CustomRangeDialog
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.FilterRow
import cc.openxiot.wematrix.ui.components.InfoChip
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import cc.openxiot.wematrix.ui.components.RangePresetChips
import cc.openxiot.wematrix.ui.modbus.RangePreset
import cc.openxiot.wematrix.ui.modbus.epochDate
import cc.openxiot.wematrix.ui.modbus.epochShort
import cc.openxiot.wematrix.ui.modbus.failureLabel
import cc.openxiot.wematrix.ui.modbus.formatEpochMillis
import cc.openxiot.wematrix.ui.theme.Gray500
import cc.openxiot.wematrix.ui.theme.Green
import cc.openxiot.wematrix.ui.theme.Red

/**
 * 项目级历史（当前项目下所有服务的采集历史），对齐 webapp-matrix 的 `pages/main/history/`。
 *
 * web 侧是一张 5 列概览表 + 一张 6 列异常表，手机上照搬没法用 —— 两张表都改映射到卡片：
 * 概览按服务一张卡，异常一条一张卡。
 *
 * 页面结构照 web 分两块，且**两块的重取时机不同**：概览（在采方法数 / 最新采集时间）与时间范围
 * 无关，只在进页面与「刷新」时取；窗口内的异常跟着时间范围走，切一档只重取它。
 *
 * 一条硬规矩：**来自服务端与用户的文本一律原样显示、永不翻译** —— 服务名、设备 did、字段名、
 * 失败消息都在其列。页面只把后端**枚举名**（失败类型）换成中文词。
 *
 * **不做** web 的「所有服务」二级页：那是一张把每个服务每个字段都摊开的宽表，手机屏上不可用。
 * 这一页已经承担了「哪个服务不对」的判断，再往下点进单服务历史页看曲线。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    rootId: String,
    onBack: () -> Unit,
    onNavigateToServiceHistory: (serviceId: String) -> Unit = {},
    viewModel: HistoryViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    var showCustomPicker by remember { mutableStateOf(false) }

    LaunchedEffect(rootId) { viewModel.load(rootId) }

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
                        text = "历史",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
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
                    IconButton(onClick = { viewModel.refresh() }) {
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
            when {
                state.isLoadingGraph && state.services.isEmpty() -> LoadingIndicator()

                // 空间图是这一页的骨架：没有服务清单就无从扇出 /current，故整页报错
                state.graphError != null && state.services.isEmpty() -> EmptyState(
                    message = state.graphError!!,
                    modifier = Modifier.clickable { viewModel.load(rootId) }
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { OverviewCard(state) }

                    item {
                        FilterCard(
                            state = state,
                            onPresetChange = { preset ->
                                viewModel.setPreset(preset)
                                // 选到自定义就把日期选择弹出来：不弹的话用户点了没反应
                                if (preset == RangePreset.CUSTOM) showCustomPicker = true
                            },
                            onEditCustomRange = { showCustomPicker = true }
                        )
                    }

                    // 某个服务取不到数：那一格显示 -，原因在这里说清，
                    // 免得把「没取到」看成「没有数据」
                    items(state.loadErrors) { message ->
                        NoticeCard(text = message, danger = true)
                    }

                    item { SectionTitle("服务概览") }

                    if (state.overviewRows.isEmpty()) {
                        item { EmptyState("这个项目下还没有 Modbus 服务") }
                    } else {
                        items(state.overviewRows, key = { it.service.id ?: it.service.hashCode().toString() }) { row ->
                            ServiceCard(
                                row = row,
                                state = state,
                                onServiceClick = onNavigateToServiceHistory
                            )
                        }
                    }

                    item { SectionTitle("采集异常") }

                    if (state.failureSummary.isNotEmpty()) {
                        item {
                            // 汇总标签：告警文本那种长串在这里是枚举名的中文标签，一行放得下
                            FilterRow(modifier = Modifier.padding(horizontal = 16.dp)) {
                                state.failureSummary.forEach { item ->
                                    InfoChip("${failureLabel(item.type, null)} × ${item.count}", Red)
                                }
                            }
                        }
                    }

                    val rows = state.failureRows
                    if (rows.isEmpty()) {
                        item {
                            EmptyState(
                                message = if (state.failuresError.isNotEmpty()) {
                                    state.failuresError
                                } else {
                                    "这段时间没有采集异常"
                                }
                            )
                        }
                    } else {
                        items(rows, key = { it.key }) { row ->
                            FailureCard(row, onServiceClick = onNavigateToServiceHistory)
                        }
                    }

                    if (state.truncated) {
                        item { NoticeCard(text = TRUNCATED_HINT, danger = false) }
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
}

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

/** 页头三项：服务数量 / 设备数量 / 采集时间 */
@Composable
private fun OverviewCard(state: HistoryUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            OverviewItem("服务数量", state.services.size.toString(), Modifier.weight(1f))
            OverviewItem("设备数量", state.deviceCount.toString(), Modifier.weight(1f))
            // 项目里最近一次采集时刻：停在这儿说明整个项目都不采了，比任何一行的异常都严重
            OverviewItem(
                label = "采集时间",
                value = state.lastRecordedAt?.let { formatEpochMillis(it) } ?: "-",
                modifier = Modifier.weight(1.6f)
            )
        }
    }
}

@Composable
private fun OverviewItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FilterCard(
    state: HistoryUiState,
    onPresetChange: (RangePreset) -> Unit,
    onEditCustomRange: () -> Unit
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
        }
    }
}

/** 自定义档当前查的是哪一段（还没选过时为空） */
private fun customRangeText(from: Long?, to: Long?): String {
    if (from == null || to == null) return "尚未选择时间范围"
    // 窗口按整天展开，故结束时刻落在次日 00:00:00 前 1ms —— 回显时收成当天
    return "${epochDate(from)} 至 ${epochDate(to)}"
}

/**
 * 一个服务的概览卡：名称点进去看它的采集历史。
 *
 * 顺序由 [HistoryUiState.overviewRows] 定：窗口内有异常的排前面 —— 这一页的常客是
 * 「项目里现在哪儿不对」，不是「服务清单」。
 */
@Composable
private fun ServiceCard(
    row: ServiceOverview,
    state: HistoryUiState,
    onServiceClick: (String) -> Unit
) {
    val service = row.service
    val did = service.did
    val device = did?.let { state.devices[it] }
    val failure = state.recentFailure(service.id)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(
                // 整张卡片可点（原先只有服务名那一小块能点，卡片其余部分按下去没反应），
                // 与 CardItem / HomeScreen 的写法一致：onClick 为空时连 clickable 都不挂
                service.id?.let { id -> Modifier.clickable { onServiceClick(id) } } ?: Modifier
            ),
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
                    text = service.name ?: service.id ?: "-",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary,
                    // 点整张卡片就行（见 Card 的 modifier），这里不再单独挂 clickable
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 依赖设备：did 是数据、原样显示；在线态只有空间图里有这台设备时才知道
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = did ?: "-",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                device?.let {
                    InfoChip(
                        text = if (it.online == true) "在线" else "离线",
                        color = if (it.online == true) Green else Gray500
                    )
                }
            }

            // 在采方法数：只有采到过的方法才在快照里，故不是服务定义里的方法总数。
            // 取不到数（那一格是 -）与「一个都没采到」（0）必须分得开
            KeyValue("方法数", state.sampledCount(row) ?: "-")
            KeyValue(
                label = "采集时间",
                value = state.recordedAt(row)?.let { formatEpochMillis(it) } ?: "-"
            )

            // 窗口内最近的一次异常：这一列才是「要不要点进去」的依据
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "采集异常",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp)
                )
                Spacer(Modifier.width(8.dp))
                if (failure != null) {
                    InfoChip(failureLabel(failure.type, failure.remoteCode), Red)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = epochShort(failure.at),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("-", style = MaterialTheme.typography.bodySmall)
                }
            }

            // 某个服务取不到数：那一格显示 -，原因在这里就地补一句
            if (row.currentError.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = row.currentError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 一条采集异常。
 *
 * 记的是「这条错误**首次出现**的时刻」，同一条错误持续存在不会重复记 ——
 * 所以这不是「每次失败一行」，时间列的读法要照着这个来。
 */
@Composable
private fun FailureCard(row: FailureRow, onServiceClick: (String) -> Unit) {
    val item = row.item

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

            // 服务还在空间图里就给个点得进去的名字；已被挪走/删掉（只剩 id 认得出）时退成纯文本
            val service = row.service
            Text(
                text = service?.name ?: row.serviceId.ifEmpty { "-" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (service != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = if (service != null) {
                    Modifier.clickable { service.id?.let(onServiceClick) }
                } else {
                    Modifier
                }
            )

            KeyValue("设备", service?.did ?: "-")
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

/** 一条就地提示：取不到数（红）/ 截断提示（灰） */
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
