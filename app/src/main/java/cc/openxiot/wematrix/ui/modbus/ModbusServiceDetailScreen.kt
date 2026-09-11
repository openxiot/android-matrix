package cc.openxiot.wematrix.ui.modbus

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.data.api.ModbusService
import cc.openxiot.wematrix.data.api.ModbusServiceFunction
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.ErrorMessage
import cc.openxiot.wematrix.ui.components.LoadingIndicator

/**
 * Modbus 服务详情。
 *
 * 只读 + 调用：可以看到方法与请求帧，可以逐条调用；**不能**新建/编辑/删除服务、
 * 不能改方法、也不自己组 Modbus 帧（帧是 web 前端生成好存在 `functions[].request` 里的）
 * —— 这是明确的能力边界，不是没做完。
 *
 * 读方法与写方法的「调用」都放开（写方法会真的动作现场设备，界面上不额外拦，
 * 但应答为空时会给「设备已收到该帧」的提示）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModbusServiceDetailScreen(
    spaceId: String,
    serviceId: String,
    onBack: () -> Unit,
    viewModel: ModbusServiceViewModel = viewModel()
) {
    val state by viewModel.detail.collectAsState()
    val invokeState by viewModel.invoke.collectAsState()

    LaunchedEffect(spaceId, serviceId) { viewModel.loadDetail(spaceId, serviceId) }

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
                        text = "服务详情",
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
            val service = state.service
            when {
                state.isLoading && service == null -> LoadingIndicator()

                state.error != null && service == null -> ErrorMessage(
                    message = state.error!!,
                    onRetry = { viewModel.loadDetail(spaceId, serviceId) }
                )

                service == null -> EmptyState("服务不存在")

                else -> {
                    // index 是方法的序号，缺省的后排到末尾
                    val functions = service.functions.sortedBy { it.index ?: Int.MAX_VALUE }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { ServiceInfoCard(service) }

                        item {
                            Text(
                                text = "方法（${functions.size}）",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        if (functions.isEmpty()) {
                            item { EmptyState("这份服务里还没有方法", modifier = Modifier.height(160.dp)) }
                        } else {
                            items(functions) { function ->
                                MethodCard(
                                    function = function,
                                    invoking = invokeState.invokingIndex == function.index,
                                    // 一次只跑一个：有调用在飞时，其余按钮也禁用（对齐 web）
                                    enabled = invokeState.invokingIndex == null,
                                    onInvoke = {
                                        function.index?.let {
                                            viewModel.invoke(spaceId, serviceId, it)
                                        }
                                    }
                                )
                            }
                        }

                        // 调用结果：错误与结果都落在方法列表下面，不弹窗（与 web 一致）
                        invokeState.error?.let { message ->
                            item { InvokeErrorCard(message) }
                        }

                        invokeState.result?.let { result ->
                            item {
                                InvokeResultCard(
                                    result = result,
                                    function = functions.find { it.index == result.functionIndex },
                                    onDismiss = { viewModel.clearInvoke() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceInfoCard(service: ModbusService) {
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
                text = service.name?.takeIf { it.isNotBlank() } ?: "未命名服务",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            DetailRow("依赖设备", service.device?.did ?: "-")
            DetailRow("调用坐标", coordinateLabel(service.device))
            DetailRow("源点表", service.configId ?: "-")
            DetailRow("定义版本", service.version?.toString() ?: "-")
            DetailRow("更新者", service.updater?.name ?: "-")
            DetailRow("最后更新", formatEpochMillis(service.updater?.timestamp))
        }
    }
}

@Composable
private fun MethodCard(
    function: ModbusServiceFunction,
    invoking: Boolean,
    enabled: Boolean,
    onInvoke: () -> Unit
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${function.index ?: "-"}.",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = function.name?.takeIf { it.isNotBlank() } ?: "-",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (invoking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onInvoke, enabled = enabled) { Text("调用") }
                }
            }

            Spacer(Modifier.height(4.dp))

            // 请求帧：完整 RTU 帧（含 CRC16），原样交给设备发送
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "请求帧",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = function.request?.takeIf { it.isNotBlank() } ?: "-",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "应答字段",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = responseSummary(function),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun InvokeErrorCard(message: String) {
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
                text = "调用失败",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(4.dp))
            // 后端原样下发的设备侧描述，如 no response from parent device、父设备错误描述
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun InvokeResultCard(
    result: InvokeResult,
    function: ModbusServiceFunction?,
    onDismiss: () -> Unit
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "调用结果",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text("关闭") }
            }

            Text(
                text = "#${result.functionIndex} · ${
                    result.functionName.takeIf { it.isNotBlank() } ?: "-"
                }",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (result.data.isEmpty()) {
                // 写方法：应答是请求回显，没有读值
                Text(
                    text = WRITE_METHOD_SHORT,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            } else {
                // 单位从该方法的应答定义里按字段名回查；值本身后端已经解好了
                result.data.forEach { (field, value) ->
                    val unit = unitOf(function, field)
                    DetailRow(
                        label = field,
                        value = formatInvokeValue(value) + if (unit != null) " $unit" else "",
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                text = "原始返回",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = invokeResultJson(result.data),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            )
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
