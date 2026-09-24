package cc.openxiot.matrix.ui.device.controller

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.repository.ControlAction
import cc.openxiot.matrix.data.repository.ControlArg
import cc.openxiot.matrix.data.repository.ControlKind
import cc.openxiot.matrix.data.repository.ControlProp
import cc.openxiot.matrix.data.repository.ControlService
import cc.openxiot.matrix.data.repository.EnumValue
import cc.openxiot.matrix.data.repository.DeviceControlParser
import cc.openxiot.matrix.data.repository.ValueRange
import cc.openxiot.matrix.ui.components.ErrorMessage
import cc.openxiot.matrix.ui.core.UiText
import cc.openxiot.matrix.ui.core.asString

/**
 * 通用设备控制界面（Compose 实时渲染，替代「无控制页」的占位文案）：
 * 一个服务一张卡片、瀑布式排列（窄屏单列 / 宽屏多列，语义对齐 web 的
 * `repeat(auto-fill, minmax(420px,1fr))`）。控制映射与控制逻辑见 [DeviceControlParser] /
 * [DeviceControllerViewModel]，与 webapp-matrix `DeviceControllerComponent` 同一套规则。
 *
 * 行控件：bool→开关；枚举≤2→分段、>2→下拉；区间→−/+；number 输入；text/hex 输入（失焦提交）；
 * 只读→文本；方法整行可点＝执行。顶部「读取」全量刷新，不做自动轮询。
 * 反馈文案（toast）来自 [DeviceControllerViewModel.events]，是非设备标签的页面自有词（中文，
 * translatable="false"，见 strings_device.xml 同款注释）。
 */
@Composable
fun DeviceControllerScreen(
    spaceId: String,
    did: String,
    type: String,
    modifier: Modifier = Modifier,
    vm: DeviceControllerViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // 一次性反馈 → toast
    LaunchedEffect(Unit) {
        vm.events.collect { msg ->
            android.widget.Toast.makeText(context, msg.asString(context), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val loading = state.isLoading
    LaunchedEffect(Unit) {
        vm.load(spaceId, did, type)
    }

    when {
        loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> Box(modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // 顶部工具栏：手动「读取」（全量刷新）
                if (state.services.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = vm::refresh,
                            enabled = !state.isRefreshing,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            if (state.isRefreshing) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            }
                            Text(stringResource(R.string.common_refresh))
                        }
                    }
                }

                val error = state.loadError
                when {
                    error != null -> ErrorMessage(
                        message = error,
                        onRetry = { vm.load(spaceId, did, type) },
                        modifier = Modifier.weight(1f)
                    )
                    state.isEmpty -> Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.dc_no_data),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                    else -> ControllerGrid(
                        services = state.services,
                        state = state.state,
                        onSwitch = { p, v -> vm.write(p, v) },
                        onEnum = { p, v -> vm.write(p, v) },
                        onStep = { p, delta -> vm.step(p, delta) },
                        onNumber = { p, v -> vm.write(p, v) },
                        onText = { p, v -> vm.write(p, v) },
                        onRead = vm::read,
                        onIdentify = vm::identify,
                        onInvoke = vm::invoke,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** 服务卡片瀑布（一个服务一张卡片）。带参方法点整行弹窗收集参数。 */
@Composable
private fun ControllerGrid(
    services: List<ControlService>,
    state: Map<String, Any?>,
    onSwitch: (ControlProp, Boolean) -> Unit,
    onEnum: (ControlProp, Any?) -> Unit,
    onStep: (ControlProp, Int) -> Unit,
    onNumber: (ControlProp, Any?) -> Unit,
    onText: (ControlProp, String) -> Unit,
    onRead: (ControlProp) -> Unit,
    onIdentify: (ControlProp) -> Unit,
    onInvoke: (ControlAction, List<Any?>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingAction by remember { mutableStateOf<ControlAction?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(360.dp),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(services, key = { it.iid }) { svc ->
            ServiceCard(
                svc = svc,
                state = state,
                onSwitch = onSwitch,
                onEnum = onEnum,
                onStep = onStep,
                onNumber = onNumber,
                onText = onText,
                onRead = onRead,
                onIdentify = onIdentify,
                onInvoke = { a ->
                    if (a.args.isEmpty()) onInvoke(a, emptyList()) else pendingAction = a
                },
            )
        }
    }

    pendingAction?.let { action ->
        ActionInvokeDialog(
            action = action,
            onDismiss = { pendingAction = null },
            onInvoke = { values ->
                val a = pendingAction
                pendingAction = null
                if (a != null) onInvoke(a, values)
            },
        )
    }
}

@Composable
private fun ServiceCard(
    svc: ControlService,
    state: Map<String, Any?>,
    onSwitch: (ControlProp, Boolean) -> Unit,
    onEnum: (ControlProp, Any?) -> Unit,
    onStep: (ControlProp, Int) -> Unit,
    onNumber: (ControlProp, Any?) -> Unit,
    onText: (ControlProp, String) -> Unit,
    onRead: (ControlProp) -> Unit,
    onIdentify: (ControlProp) -> Unit,
    onInvoke: (ControlAction) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            ServiceHeader(svc)
            if (svc.isInfo) {
                // 信息卡：只展示（可点「点亮设备」/单读）
                svc.props.forEachIndexed { i, p ->
                    InfoRow(p = p, value = state[p.key], onRead = { onRead(p) }, onIdentify = { onIdentify(p) })
                    if (i < svc.props.lastIndex) Divider()
                }
            } else {
                svc.props.forEachIndexed { i, p ->
                    PropRow(
                        p = p,
                        value = state[p.key],
                        onSwitch = { v -> onSwitch(p, v) },
                        onEnum = { v -> onEnum(p, v) },
                        onStep = { d -> onStep(p, d) },
                        onNumber = { v -> onNumber(p, v) },
                        onText = { s -> onText(p, s) },
                        onRead = { onRead(p) },
                    )
                    if (i < svc.props.lastIndex || svc.actions.isNotEmpty()) Divider()
                }
                svc.actions.forEachIndexed { i, a ->
                    ActionRow(a = a, onClick = { onInvoke(a) })
                    if (i < svc.actions.lastIndex) Divider()
                }
            }
        }
    }
}

@Composable
private fun ServiceHeader(svc: ControlService) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            text = svc.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (svc.isInfo) {
            Text(
                text = svc.en,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 信息服务行：名称在左，右侧为取值（或「点亮设备」按钮 / 单读按钮）。 */
@Composable
private fun InfoRow(
    p: ControlProp,
    value: Any?,
    onRead: () -> Unit,
    onIdentify: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = p.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (p.identify) {
            OutlinedButton(onClick = onIdentify, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                Text(stringResource(R.string.dc_identify), style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Text(
                text = displayValue(p, value),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
            if (p.readable) {
                Spacer(Modifier.width(10.dp))
                ReadButton(onClick = onRead)
            }
        }
    }
}

@Composable
private fun PropRow(
    p: ControlProp,
    value: Any?,
    onSwitch: (Boolean) -> Unit,
    onEnum: (Any?) -> Unit,
    onStep: (Int) -> Unit,
    onNumber: (Any?) -> Unit,
    onText: (String) -> Unit,
    onRead: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = p.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        val kind = p.kind
        when {
            kind == ControlKind.SWITCH -> Switch(checked = value as? Boolean == true, onCheckedChange = onSwitch)
            kind == ControlKind.SEG -> SegChips(p.list.orEmpty(), value, onEnum)
            kind == ControlKind.SEL -> SelMenu(p, value, onEnum)
            kind == ControlKind.STEP -> StepControl(p, value, onStep)
            kind == ControlKind.NUMBER -> CommitField(
                value = value,
                keyboardType = KeyboardType.Number,
                mono = false,
                onCommit = { s -> onNumber(s) },
                modifier = Modifier.width(120.dp)
            )
            kind == ControlKind.TEXT || kind == ControlKind.HEX -> CommitField(
                value = value,
                keyboardType = KeyboardType.Text,
                mono = kind == ControlKind.HEX,
                onCommit = onText,
                modifier = Modifier.width(140.dp)
            )
            else -> Text(
                text = displayValue(p, value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        // 可写行的单位点缀
        if (p.writable && p.kind != null) {
            val u = unitText(p)
            if (u.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(text = u, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (p.readable && p.kind == null) {
            Spacer(Modifier.width(10.dp))
            ReadButton(onClick = onRead)
        }
    }
}

@Composable
private fun SegChips(list: List<EnumValue>, value: Any?, onEnum: (Any?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEach { o ->
            FilterChip(
                selected = o.value?.toString() == value?.toString(),
                onClick = { onEnum(o.value) },
                label = { Text(o.label) }
            )
        }
    }
}

@Composable
private fun SelMenu(p: ControlProp, value: Any?, onEnum: (Any?) -> Unit) {
    val options = p.list.orEmpty()
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value?.toString() == value?.toString() }?.label
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.height(40.dp)
        ) {
            Text(text = current ?: p.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o.label) },
                    onClick = { expanded = false; onEnum(o.value) }
                )
            }
        }
    }
}

@Composable
private fun StepControl(p: ControlProp, value: Any?, onStep: (Int) -> Unit) {
    val range = p.range ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onStep(-1) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(36.dp)) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        Text(text = stepDisplay(value, range), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { onStep(1) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(36.dp)) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** 数字 / 文本 / hex 输入：本地保存草稿，失焦（或软键盘 Done）时才提交。 */
@Composable
private fun CommitField(
    value: Any?,
    keyboardType: KeyboardType,
    mono: Boolean,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    var dirty by remember { mutableStateOf(false) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; dirty = true },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (dirty) { dirty = false; onCommit(text) }
            }),
            modifier = Modifier.onFocusChanged { fc ->
                if (!fc.isFocused && dirty) { dirty = false; onCommit(text) }
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (mono) FontFamily.Monospace else null
            ),
            shape = MaterialTheme.shapes.medium
        )
    }
}

@Composable
private fun ActionRow(a: ControlAction, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = a.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 带参方法的参数收集弹窗：每个入参一行，枚举（value-list）用下拉，其余用文本输入
 * （数值 format 时取数）。确认后按 [a.args] 顺序回传规范化后的 value 列表。
 */
@Composable
private fun ActionInvokeDialog(
    action: ControlAction,
    onDismiss: () -> Unit,
    onInvoke: (List<Any?>) -> Unit,
) {
    val drafts = remember(action) { action.args.map { mutableStateOf(argDefault(it)) }.toTypedArray() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(action.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                action.args.forEachIndexed { i, arg ->
                    Text(arg.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (arg.list.isNullOrEmpty()) {
                        OutlinedTextField(
                            value = drafts[i].value,
                            onValueChange = { drafts[i].value = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (isNumberFormat(arg.format)) KeyboardType.Number else KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        var expanded by remember { mutableStateOf(false) }
                        val current = arg.list.firstOrNull { it.value?.toString() == drafts[i].value }?.label
                        Box {
                            OutlinedButton(
                                onClick = { expanded = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(current ?: arg.list.first().label, maxLines = 1)
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                arg.list.forEach { o ->
                                    DropdownMenuItem(
                                        text = { Text(o.label) },
                                        onClick = { expanded = false; drafts[i].value = o.value?.toString().orEmpty() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onInvoke(action.args.mapIndexed { i, arg -> draftValue(arg, drafts[i].value) })
            }) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

private fun argDefault(arg: ControlArg): String =
    arg.list?.firstOrNull()?.value?.toString() ?: if (arg.format == "bool") "false" else ""

/** 枚举入参回传其原始 value 类型；数值入参转 Number；bool 转 Boolean；文本原样。 */
private fun draftValue(arg: ControlArg, text: String): Any? {
    arg.list?.firstOrNull { it.value?.toString() == text }?.let { return it.value }
    return when {
        arg.format == "bool" -> text.toBoolean()
        isNumberFormat(arg.format) -> text.toDoubleOrNull() ?: text
        else -> text
    }
}

private fun isNumberFormat(format: String): Boolean =
    format in setOf("int8", "int16", "int32", "uint8", "uint16", "float", "double")

@Composable
private fun ReadButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(stringResource(R.string.common_refresh), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Divider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

// ===================== 展示格式化 =====================

/** 只读/信息卡取值展示：bool→开/关，枚举→标签，数值→原值+单位。 */
@Composable
private fun displayValue(p: ControlProp, value: Any?): String {
    if (value == null) return "—"
    if (p.format == "bool") {
        return stringResource(if (value as? Boolean == true) R.string.common_switch_on else R.string.common_switch_off)
    }
    val label = DeviceControlParser.listLabel(p.list, value)
    if (label != null) return label
    return value.toString() + unitText(p)
}

@Composable
private fun unitText(p: ControlProp): String {
    val u = p.unit ?: return ""
    return when (u) {
        "celsius" -> " °C"
        "kelvin" -> " K"
        "percentage", "percent" -> "%"
        else -> " $u"
    }
}

@Composable
private fun stepDisplay(value: Any?, range: ValueRange): String {
    val n = (value as? Number)?.toDouble() ?: range.min
    val step = range.step
    return if (step != null && step < 1) {
        java.math.BigDecimal.valueOf(n).setScale(1, java.math.RoundingMode.HALF_UP).toDouble().toString()
    } else {
        n.toString()
    }
}

/** VM 步进回调：夹在 [min,max]，步长默认 1（已按整步缩放到 3 位小数）。 */
fun DeviceControllerViewModel.step(p: ControlProp, delta: Int) {
    val range = p.range ?: return
    val n = (uiState.value.state[p.key] as? Number?)?.toDouble() ?: range.min
    val step = range.step ?: 0.0
    val next = ((n + delta * step) * 1000).toInt() / 1000.0
    write(p, Math.max(range.min, Math.min(range.max, next)))
}