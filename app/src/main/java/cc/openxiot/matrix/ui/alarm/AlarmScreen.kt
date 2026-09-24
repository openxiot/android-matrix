package cc.openxiot.matrix.ui.alarm

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.api.ModbusAlarm
import cc.openxiot.matrix.data.api.ModbusAlarmLevelCount
import cc.openxiot.matrix.data.api.ModbusAlarmList
import cc.openxiot.matrix.data.api.ModbusAlarmTextCount
import cc.openxiot.matrix.data.api.ModbusServiceBrief
import cc.openxiot.matrix.ui.components.CustomRangeDialog
import cc.openxiot.matrix.ui.components.EmptyState
import cc.openxiot.matrix.ui.components.FilterDropdown
import cc.openxiot.matrix.ui.components.FilterOption
import cc.openxiot.matrix.ui.components.FilterRow
import cc.openxiot.matrix.ui.components.InfoChip
import cc.openxiot.matrix.ui.components.LoadingIndicator
import cc.openxiot.matrix.ui.components.RangePresetChips
import cc.openxiot.matrix.ui.core.asString
import cc.openxiot.matrix.ui.modbus.RangePreset
import cc.openxiot.matrix.ui.modbus.alarmCloseLabel
import cc.openxiot.matrix.ui.modbus.alarmCondition
import cc.openxiot.matrix.ui.modbus.alarmLevelLabel
import cc.openxiot.matrix.ui.modbus.alarmLevels
import cc.openxiot.matrix.ui.modbus.alarmSampleText
import cc.openxiot.matrix.ui.modbus.epochDate
import cc.openxiot.matrix.ui.modbus.epochShort
import cc.openxiot.matrix.ui.modbus.formatEpochMillis
import cc.openxiot.matrix.ui.theme.Blue500
import cc.openxiot.matrix.ui.theme.Gray500
import cc.openxiot.matrix.ui.theme.Green
import cc.openxiot.matrix.ui.theme.Orange
import cc.openxiot.matrix.ui.theme.Red

/**
 * 阈值告警清单（对齐 webapp-matrix 的 `/main/alarm`）。
 *
 * web 侧是一张 10 列表格，手机上照搬没法用 —— 同一批字段改映射到卡片：时间与级别当抬头，
 * 服务 · 方法 · 出值当一行，触发条件 / 告警文本 / 当前值各一行，状态与处理压到底部。
 *
 * 两条硬规矩：
 * - **服务端与用户的文本一律原样显示、永不翻译**：告警文本（用户自己填的）、出值名、取值表的描述、
 *   单位、服务名、处理人姓名都在其列。页面只把后端**枚举名**（级别 / 关闭原因）换成中文词。
 * - **「处理」是就地替换那一行**，不整页刷新：用户刚点过的那条要立刻变成「已处理」。
 *
 * `rootId` 是当前项目根空间，既是鉴权作用域也是查询范围（含子空间）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(
    rootId: String,
    onBack: () -> Unit,
    onNavigateToServiceHistory: (serviceId: String) -> Unit = {},
    viewModel: AlarmViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // 自定义区间弹窗的开合。窗口本身存在 VM 里（那是查询条件），这里只管弹窗
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    Text(
                        text = stringResource(R.string.alarm_title),
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
                    IconButton(onClick = { viewModel.loadAlarms() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh))
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
            // 空间图是骨架、清单是内容：只有骨架在加载时才整页转圈；
            // 空间图失败不挡清单（服务名退回 id，见 AlarmViewModel.load）
            if (state.isLoadingGraph && state.alarms == null) {
                LoadingIndicator()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterCard(
                            state = state,
                            onPresetChange = { preset ->
                                viewModel.setPreset(preset)
                                // 选到自定义就把日期选择弹出来：不弹的话用户点了没反应
                                if (preset == RangePreset.CUSTOM) showCustomPicker = true
                            },
                            onServiceChange = viewModel::setService,
                            onLevelChange = viewModel::setLevel,
                            onHandledChange = viewModel::setHandled,
                            onOpenChange = viewModel::setOpen,
                            onEditCustomRange = { showCustomPicker = true }
                        )
                    }

                    // 取数失败：整页报错而不是显示 0 条 —— 「查不到」与「没有告警」是两件事
                    state.error?.let { message ->
                        item {
                            ErrorCard(
                                message = message.asString(),
                                onRetry = { viewModel.loadAlarms() }
                            )
                        }
                    }

                    // 处理失败的反馈：就地一条，不动清单
                    state.actionError?.let { message ->
                        item { NoticeCard(text = message.asString(), danger = true) }
                    }
                    state.handledId?.let {
                        item { NoticeCard(text = stringResource(R.string.alarm_action_ok), danger = false) }
                    }

                    state.alarms?.let { list ->
                        item { SummaryCard(list) }

                        if (list.items.isEmpty()) {
                            item {
                                EmptyState(
                                    stringResource(R.string.alarm_empty),
                                    modifier = Modifier.height(200.dp)
                                )
                            }
                        } else {
                            // 键优先用告警 id（处理后是就地替换那一行，id 不变 → 位置不乱跳）；
                            // 没有 id 的老数据退回行号 —— 拿内容做键的话，两条一模一样的告警会撞，
                            // 而 LazyColumn 的键撞了是直接抛异常，整页白掉
                            itemsIndexed(
                                list.items,
                                key = { index, alarm -> alarm.id ?: "row$index" }
                            ) { _, alarm ->
                                AlarmCard(
                                    alarm = alarm,
                                    services = state.services,
                                    handling = state.handlingId == alarm.id,
                                    onHandle = { viewModel.handle(alarm) },
                                    onServiceClick = onNavigateToServiceHistory
                                )
                            }
                        }

                        if (list.truncated) {
                            item { NoticeCard(text = stringResource(R.string.common_truncated_hint), danger = false) }
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
}

/**
 * 级别标签的配色（INFO 蓝 / WARN 橙 / CRITICAL 红）。
 *
 * 与后端的分级口径一一对应，不按「严重度」另分一套 —— 这三个词描述的是**要不要人管**。
 */
private fun levelColor(level: String?): Color = when (level) {
    "INFO" -> Blue500
    "WARN" -> Orange
    "CRITICAL" -> Red
    else -> Gray500
}

@Composable
private fun FilterCard(
    state: AlarmUiState,
    onPresetChange: (RangePreset) -> Unit,
    onServiceChange: (String?) -> Unit,
    onLevelChange: (String?) -> Unit,
    onHandledChange: (Boolean?) -> Unit,
    onOpenChange: (Boolean?) -> Unit,
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

            // 自定义档要能看见并改回自己选的窗口：chips 只表达「档位」，
            // 具体是哪两天得另说一句，否则用户看不出当前查的是哪天
            if (state.preset == RangePreset.CUSTOM) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = customRangeText(state.customFrom, state.customTo),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onEditCustomRange) { Text(stringResource(R.string.common_change)) }
                }
            }

            FilterRow {
                // 不做「所有服务」那一项：不选就是全部，占位符说的正是这件事
                FilterDropdown(
                    label = stringResource(R.string.common_service_label),
                    value = state.serviceId,
                    options = state.services.mapNotNull { service ->
                        service.id?.let { FilterOption(it, service.name ?: it) }
                    },
                    onSelect = onServiceChange,
                    placeholder = stringResource(R.string.alarm_all_services)
                )
                FilterDropdown(
                    label = stringResource(R.string.alarm_level_label),
                    value = state.level,
                    options = alarmLevels.map { FilterOption(it, alarmLevelLabel(it).asString()) },
                    onSelect = onLevelChange
                )
                // 两个三态筛选：「不限」是菜单里单独的一项，与明确的 false 完全不是一回事
                FilterDropdown(
                    label = stringResource(R.string.alarm_handled_label),
                    value = state.handled,
                    options = listOf(
                        FilterOption(false, stringResource(R.string.common_unhandled)),
                        FilterOption(true, stringResource(R.string.common_handled))
                    ),
                    onSelect = onHandledChange
                )
                FilterDropdown(
                    label = stringResource(R.string.alarm_status_label),
                    value = state.open,
                    options = listOf(
                        FilterOption(false, stringResource(R.string.common_unrecovered)),
                        FilterOption(true, stringResource(R.string.common_recovered))
                    ),
                    onSelect = onOpenChange
                )
            }
        }
    }
}

/**
 * 自定义档当前查的是哪一段（还没选过时为空）。
 *
 * 两个分支都是文案，所以整个函数改成 `@Composable`：它只被上面筛选栏里那一个
 * `Text` 调用，返回资源 id 再让调用方 `stringResource` 反而绕一圈。
 */
@Composable
private fun customRangeText(from: Long?, to: Long?): String {
    if (from == null || to == null) return stringResource(R.string.common_no_range)
    // 按整天展开，故结束时刻落在次日 00:00:00 前 1ms —— 回显时收成当天，别显示成 23:59:59
    return stringResource(R.string.common_range_text, epochDate(from), epochDate(to))
}

/**
 * 汇总卡：三个计数 + 两组标签。
 *
 * 数字与分布都只统计**本次返回的这批行**（后端的口径），故要连着 `truncated` 一起读 ——
 * 截断时这些数字是「最近 N 条里的」，不是全部。
 */
@Composable
private fun SummaryCard(list: ModbusAlarmList) {
    val summary = list.summary
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryCount(stringResource(R.string.alarm_summary_title), "${summary.total}", Modifier.weight(1f))
                SummaryCount(stringResource(R.string.common_unhandled), "${summary.unhandled}", Modifier.weight(1f))
                SummaryCount(stringResource(R.string.common_unrecovered), "${summary.open}", Modifier.weight(1f))
            }

            if (summary.byLevel.isNotEmpty()) {
                TagRow(
                    label = stringResource(R.string.alarm_level_label),
                    tags = summary.byLevel.map { item: ModbusAlarmLevelCount ->
                        TagItem(
                            alarmLevelLabel(item.level).asString(),
                            levelColor(item.level),
                            "× ${item.count}"
                        )
                    }
                )
            }
            if (summary.byText.isNotEmpty()) {
                TagRow(
                    label = stringResource(R.string.alarm_text_label),
                    // 告警文本是用户自己填的：原样显示、永不翻译（翻译它等于改用户的数据）。
                    // 兜底桶 UNKNOWN（后端给既没有级别/文本的脏数据归的桶）也照原样走
                    tags = summary.byText.map { item: ModbusAlarmTextCount ->
                        TagItem(item.text, Gray500, "× ${item.count}")
                    }
                )
            }
        }
    }
}

@Composable
private fun SummaryCount(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class TagItem(val text: String, val color: Color, val count: String)

@Composable
private fun TagRow(label: String, tags: List<TagItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // 用 FlowRow 会把标签排成不定高的多行；这里标签本来就不多（级别最多三个），
        // 直接横滑一行，长文本（告警文本是用户敲的）也不会把卡片撑破
        FilterRow {
            tags.forEach { tag ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoChip(tag.text, tag.color)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = tag.count,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: ModbusAlarm,
    services: List<ModbusServiceBrief>,
    handling: Boolean,
    onHandle: () -> Unit,
    onServiceClick: (String) -> Unit
) {
    val recovered = alarm.recoveredAt != null
    val handled = alarm.handled == true

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
            // 抬头：越限**首次出现**的时刻 + 级别
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatEpochMillis(alarm.at),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                InfoChip(alarmLevelLabel(alarm.level).asString(), levelColor(alarm.level))
            }

            // 服务名：服务还在空间图里就可点（去它的采集历史），已被挪走/删掉时退回 serviceId
            val service = services.find { it.id == alarm.serviceId }
            Text(
                text = service?.name ?: alarm.serviceId ?: "-",
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
                    Modifier.clickable { onServiceClick(service.id ?: return@clickable) }
                } else {
                    Modifier
                }
            )

            // 方法序号 + 出值名（**数据、不翻译**：与点表里的字段名逐字相同，翻了就对不上）
            KeyValueRow(stringResource(R.string.common_function_label), "#${alarm.functionIndex}")
            KeyValueRow(stringResource(R.string.alarm_field_label), alarm.field.ifEmpty { "-" })
            // 触发条件是**行内快照**：定义改了不该改写历史告警的含义
            KeyValueRow(
                stringResource(R.string.alarm_trigger_label),
                alarmCondition(alarm).asString()
            )
            KeyValueRow(stringResource(R.string.alarm_text_label), alarm.text?.ifEmpty { null } ?: "-")
            KeyValueRow(stringResource(R.string.alarm_current_value_label), alarmSampleText(alarm))

            Spacer(Modifier.height(4.dp))

            // 状态：恢复与否 + 怎么关掉的。三条关闭路径都得露脸 —— 一条没有恢复样本的关闭
            // （定义不再覆盖这个键、或被同出值的另一条规则接管）看起来与「值回来了」一模一样
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoChip(stringResource(if (recovered) R.string.common_recovered else R.string.common_unrecovered), if (recovered) Gray500 else Red)
                if (alarm.recoveredAt != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        // 与 web 同口径：这张卡上旁边还有关闭原因要放，年份省掉
                        text = epochShort(alarm.recoveredAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                alarm.closeType?.takeIf { it.isNotEmpty() }?.let { closeType ->
                    Spacer(Modifier.width(6.dp))
                    // 正文走标签、原始枚举名不露脸（web 放在 title 上，手机上没地方挂 —— 见下方说明）
                    Text(
                        text = alarmCloseLabel(closeType).asString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoChip(stringResource(if (handled) R.string.common_handled else R.string.common_unhandled), if (handled) Green else Orange)
                if (handled) {
                    val name = alarm.handledBy?.name
                    val at = alarm.handledBy?.timestamp
                    if (!name.isNullOrEmpty() || at != null) {
                        Spacer(Modifier.width(6.dp))
                        // 处理人与处理时刻都是服务端给的（名字是账号数据），原样显示
                        Text(
                            text = listOfNotNull(
                                name?.takeIf { it.isNotEmpty() },
                                at?.let { epochShort(it) }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                    if (handling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(
                            onClick = onHandle,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) { Text(stringResource(R.string.common_handle)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
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
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) { Text(stringResource(R.string.common_retry)) }
        }
    }
}

/** 一条就地提示：处理失败（红）/ 截断提示与操作成功（灰） */
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
