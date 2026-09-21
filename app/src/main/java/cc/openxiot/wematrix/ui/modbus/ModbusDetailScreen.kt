package cc.openxiot.wematrix.ui.modbus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.R
import cc.openxiot.wematrix.data.api.ModbusCommand
import cc.openxiot.wematrix.data.api.ModbusConfig
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.ErrorMessage
import cc.openxiot.wematrix.ui.components.InfoChip
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import cc.openxiot.wematrix.ui.core.asString
import cc.openxiot.wematrix.ui.theme.Red

/**
 * 设备点表详情（只读）。
 *
 * **只读是明确的能力边界，不是没做完**：新建/编辑/删除功能码、拖拽排序、发布与生命周期流转
 * 都要写接口，移动端不做。所以这里除了返回没有第二个按钮 —— 将来要加，先想清楚权限口径。
 *
 * 唯一的例外是每行的「命令」：它**不写任何东西**，只是照着这份定义当场算出请求帧与两条应答帧
 * 给现场排障看（见 [CommandFrameDialog]）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModbusDetailScreen(
    configId: String,
    onBack: () -> Unit,
    viewModel: ModbusViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val config = state.detail

    // 正在预览「命令」的那条功能码；null = 没开对话框
    var previewCommand by remember { mutableStateOf<ModbusCommand?>(null) }

    LaunchedEffect(configId) { viewModel.loadDetail(configId) }

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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                    Text(
                        text = stringResource(R.string.modbus_detail_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
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
                state.isDetailLoading && config == null -> LoadingIndicator()

                state.detailError != null && config == null -> ErrorMessage(
                    message = state.detailError!!,
                    onRetry = { viewModel.loadDetail(configId) }
                )

                // 与仓库层那条「点表不存在」逐字同一句，故复用同一个 key（不另立一条）
                config == null -> EmptyState(stringResource(R.string.err_modbus_point_table_missing))

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { DeviceInfoCard(config) }

                    item {
                        Text(
                            text = stringResource(R.string.modbus_command_count, config.commands.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    if (config.commands.isEmpty()) {
                        item {
                            EmptyState(
                                stringResource(R.string.modbus_command_empty),
                                modifier = Modifier.height(160.dp)
                            )
                        }
                    } else {
                        // index 是点表内的顺序（后端生成虚拟设备实例时 action 的 iid 就是它），
                        // 缺省的后排到末尾
                        val commands = config.commands.sortedBy { it.index ?: Int.MAX_VALUE }
                        items(commands) { command ->
                            CommandCard(command) { previewCommand = command }
                        }
                    }
                }
            }
        }
    }

    // 帧是照着这份定义当场算的（见 CommandFrameDialog），不发送任何东西。
    // 从站地址取自点表的设备信息：缺省时对话框会说「生成不了」而不是拿 0 兜
    previewCommand?.let { command ->
        CommandFrameDialog(
            command = command,
            slaveId = config?.slave?.slaveId,
            onDismiss = { previewCommand = null }
        )
    }
}

@Composable
private fun DeviceInfoCard(config: ModbusConfig) {
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
            Text(
                text = config.displayName ?: stringResource(R.string.modbus_config_unnamed),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            DetailRow(stringResource(R.string.modbus_label_manufacturer), config.slave?.manufacturer ?: "-")
            DetailRow(stringResource(R.string.modbus_label_model), config.slave?.model ?: "-")
            DetailRow(
                stringResource(R.string.modbus_label_slave_address),
                config.slave?.slaveId?.toString() ?: "-"
            )
            DetailRow(stringResource(R.string.modbus_label_description), config.slave?.description ?: "-")

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.modbus_label_visibility),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                InfoChip(
                    visibilityLabel(config.visibility).asString(),
                    visibilityColor(config.visibility)
                )
                Spacer(Modifier.width(6.dp))
                InfoChip(
                    lifecycleLabel(config.lifecycle).asString(),
                    lifecycleColor(config.lifecycle)
                )
            }

            DetailRow(stringResource(R.string.modbus_label_creator), config.creator?.name ?: "-")
            DetailRow(
                stringResource(R.string.modbus_label_created_at),
                formatEpochMillis(config.creator?.timestamp)
            )
            DetailRow(
                stringResource(R.string.modbus_label_updated_at),
                formatEpochMillis(config.updater?.timestamp)
            )
        }
    }
}

@Composable
private fun CommandCard(command: ModbusCommand, onShowCommand: () -> Unit) {
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
                    text = "${command.index ?: "-"}.",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = command.name?.takeIf { it.isNotBlank() } ?: "-",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    // 写功能码（05/06/0F/10）标红，读功能码（01–04）保持常规色，与 web 表格一致
                    text = "${command.fc ?: "-"} ${fcLabel(command.fc).asString()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isWriteFc(command.fc)) Red else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(4.dp))

            // 标签给的是资源 id（规则 A），值是一条还没定语言的 UiText
            commandFields(command).forEach { (labelRes, value) ->
                DetailRow(stringResource(labelRes), value.asString(), MaterialTheme.typography.bodySmall)
            }

            // 01/02 的逐位命名：位区读回来是一段掩码，用户在这里填的位名决定它拆成哪几个字段
            bitNameRows(command).takeIf { it.isNotEmpty() }?.let { rows ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.modbus_bit_name_count, rows.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                rows.forEach { (offset, name) ->
                    DetailRow(
                        stringResource(R.string.modbus_bit_row, offset),
                        name,
                        MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 03/04 的应答字段名：读回来的每个值将来叫什么（服务里 response[].field 就是它）
            fieldNameRows(command).takeIf { it.isNotEmpty() }?.let { rows ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.modbus_field_name_count, rows.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                rows.forEachIndexed { i, name ->
                    DetailRow(
                        stringResource(R.string.modbus_field_row, i + 1),
                        name,
                        MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // 本端只预览、不发送：帧是照着这份定义当场算出来的
            TextButton(
                onClick = onShowCommand,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    stringResource(R.string.modbus_command_action),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = textStyle,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
