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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.data.api.ModbusCommand
import cc.openxiot.wematrix.data.api.ModbusConfig
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.ErrorMessage
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import cc.openxiot.wematrix.ui.theme.Red

/**
 * 设备点表详情（只读）。
 *
 * **只读是明确的能力边界，不是没做完**：新建/编辑/删除功能码、拖拽排序、发布与生命周期流转、
 * 命令帧（请求帧）预览，这些要么需要写接口、要么需要本端生成 Modbus 帧，移动端都不做。
 * 所以这里除了返回没有第二个按钮 —— 将来要加，先想清楚权限口径。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModbusDetailScreen(
    configId: String,
    onBack: () -> Unit,
    viewModel: ModbusViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text(
                        text = "设备点表详情",
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
            val config = state.detail
            when {
                state.isDetailLoading && config == null -> LoadingIndicator()

                state.detailError != null && config == null -> ErrorMessage(
                    message = state.detailError!!,
                    onRetry = { viewModel.loadDetail(configId) }
                )

                config == null -> EmptyState("设备点表不存在")

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { DeviceInfoCard(config) }

                    item {
                        Text(
                            text = "功能码（${config.commands.size}）",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    if (config.commands.isEmpty()) {
                        item { EmptyState("暂无功能码", modifier = Modifier.height(160.dp)) }
                    } else {
                        // index 是点表内的顺序（后端生成虚拟设备实例时 action 的 iid 就是它），
                        // 缺省的后排到末尾
                        val commands = config.commands.sortedBy { it.index ?: Int.MAX_VALUE }
                        items(commands) { command -> CommandCard(command) }
                    }
                }
            }
        }
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
                text = config.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            DetailRow("厂家", config.slave?.manufacturer ?: "-")
            DetailRow("型号", config.slave?.model ?: "-")
            DetailRow("从站地址", config.slave?.slaveId?.toString() ?: "-")
            DetailRow("描述", config.slave?.description ?: "-")

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "可见度",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                InfoChip(
                    visibilityLabel(config.visibility),
                    visibilityColor(config.visibility)
                )
                Spacer(Modifier.width(6.dp))
                InfoChip(
                    lifecycleLabel(config.lifecycle),
                    lifecycleColor(config.lifecycle)
                )
            }

            DetailRow("创建者", config.creator?.name ?: "-")
            DetailRow("创建时间", formatEpochMillis(config.creator?.timestamp))
            DetailRow("最后更新", formatEpochMillis(config.updater?.timestamp))
        }
    }
}

@Composable
private fun CommandCard(command: ModbusCommand) {
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
                    text = "${command.fc ?: "-"} ${fcLabel(command.fc)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isWriteFc(command.fc)) Red else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(4.dp))

            commandFields(command).forEach { (label, value) ->
                DetailRow(label, value, MaterialTheme.typography.bodySmall)
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
