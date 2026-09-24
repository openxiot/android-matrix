package cc.openxiot.matrix.ui.modbus

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.api.ModbusService
import cc.openxiot.matrix.data.api.ModbusServiceFunction
import cc.openxiot.matrix.ui.components.EmptyState
import cc.openxiot.matrix.ui.components.ErrorMessage
import cc.openxiot.matrix.ui.components.LoadingIndicator
import cc.openxiot.matrix.ui.core.asString

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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                    Text(
                        text = stringResource(R.string.modbus_service_detail_title),
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

                // 与仓库层那条「服务不存在」逐字同一句，故复用同一个 key（不另立一条）
                service == null -> EmptyState(stringResource(R.string.err_modbus_service_missing))

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
                                text = stringResource(
                                    R.string.modbus_service_method_count,
                                    functions.size
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        if (functions.isEmpty()) {
                            item {
                                EmptyState(
                                    stringResource(R.string.modbus_service_method_empty),
                                    modifier = Modifier.height(160.dp)
                                )
                            }
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
                            item { InvokeErrorCard(message.asString()) }
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
                text = service.name?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.modbus_service_unnamed),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            DetailRow(stringResource(R.string.modbus_service_label_device), service.device?.did ?: "-")
            DetailRow(
                stringResource(R.string.modbus_service_label_coordinate),
                coordinateLabel(service.device).asString()
            )
            DetailRow(stringResource(R.string.modbus_service_label_source), service.configId ?: "-")
            DetailRow(
                stringResource(R.string.modbus_service_label_version),
                service.version?.toString() ?: "-"
            )
            DetailRow(
                stringResource(R.string.modbus_service_label_updater),
                service.updater?.name ?: "-"
            )
            DetailRow(
                stringResource(R.string.modbus_label_updated_at),
                formatEpochMillis(service.updater?.timestamp)
            )
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
                    TextButton(onClick = onInvoke, enabled = enabled) {
                        Text(stringResource(R.string.modbus_service_invoke))
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // 请求：v2 定义里 request 是结构化对象（slaveId/fc/start/quantity|fields），不是完整帧。
            // 本端不自己组帧 —— 帧由后端在 invoke 时现组（含 CRC16）；这里只展示方向摘要。
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    // 与「命令」对话框里那张帧的标题同一句，共用一个 key
                    text = stringResource(R.string.modbus_frame_request),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = describeFunctionRequest(function),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.modbus_service_response_fields),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = responseSummary(function),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )

            // 位清单：位是独立的结果键，调用后会与父字段一起出现在返回值里，
            // 光看上面那行「整段位掩码」不知道里面还拆出了哪几位
            bitListRows(function).takeIf { it.isNotEmpty() }?.let { rows ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.modbus_service_response_bits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                rows.forEach { (field, bit) ->
                    Text(
                        text = "$field → $bit",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 自动轮询与调用周期：两者是一件事的两面（跑不跑 / 多久跑一次），并排一行放。
            // 写方法恒为 -（周期调用写方法等于周期性往寄存器里写值，后端直接拒）
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.modbus_service_polling),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                val polling = pollingLabel(function).asString()
                Text(
                    text = polling,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isPollingOff(function)) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.modbus_service_interval),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    // 停用时也照常显示周期 —— 那是留着待用的配置
                    text = scheduleLabel(function).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            // 告警配置（只读）：列出每个出值配了哪几条规则。能否编辑由 web 的服务编辑页管，
            // 移动端只让用户看明白「这个出值配了告警没有、配的是什么」
            alarmedOutputs(function).takeIf { it.isNotEmpty() }?.let { outputs ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.modbus_service_alarm_count,
                        definedAlarmCount(function)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 规则之间的顿号也要跟着语言变（同上：全角标点，漏抽门禁看不见）。
                // 每条规则先在这儿解析成字符串再 join：joinToString 的 lambda 不是 @Composable，
                // 在里面取不到资源。
                val alarmSeparator = stringResource(R.string.modbus_service_alarm_separator)
                outputs.forEach { output ->
                    val rules = output.alarms.map { alarm ->
                        val brief = alarmRuleBrief(alarm)
                        // 停用的规则也算进条数，但要标出来 —— 否则用户会以为它在生效
                        if (alarm.enabled == true) {
                            brief
                        } else {
                            stringResource(R.string.modbus_service_alarm_disabled, brief)
                        }
                    }
                    Text(
                        text = output.key + "  " + rules.joinToString(alarmSeparator),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
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
                // 与仓库层那条调用失败的消息逐字同一句，故复用同一个 key（不另立一条）
                text = stringResource(R.string.err_modbus_service_invoke),
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
                    text = stringResource(R.string.modbus_service_invoke_result),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
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
                    text = stringResource(R.string.modbus_service_write_short),
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
                text = stringResource(R.string.modbus_service_raw_response),
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
