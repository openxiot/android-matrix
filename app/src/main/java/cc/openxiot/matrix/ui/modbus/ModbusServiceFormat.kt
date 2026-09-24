package cc.openxiot.matrix.ui.modbus

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.api.ModbusServiceDevice
import cc.openxiot.matrix.data.api.ModbusServiceField
import cc.openxiot.matrix.data.api.ModbusServiceFieldAlarm
import cc.openxiot.matrix.data.api.ModbusServiceFunction
import cc.openxiot.matrix.ui.core.UiText
import com.google.gson.Gson

/**
 * Modbus 服务的展示口径，对齐 webapp-matrix 的
 * `pages/main/device/services/service/detail/device.service.detail.component.*`。
 *
 * 与 [ModbusFormat] 分开：那份是「设备点表」的口径（功能码、逻辑地址），这份是「服务」的口径
 * （调用坐标、请求帧、应答字段、调用结果）。
 *
 * 这里只放「纯函数」，配色之类 Compose 类型留在各 Screen 里。
 *
 * 全文件唯一的例外是 [responseSummary]：它要按语言选分隔符（中英用不同的逗号）并拼一整句提示，
 * 而 `joinToString` 拼出来的东西没法是一条 `UiText`，故它改 `@Composable`、直接在组合里取资源。
 * 也正因为这样，那些 `const val` 的提示语全部删掉了 —— 常量取不到 Context。
 */

/**
 * v2 起功能码直接存在 `request.fc` 里，不再从 hex 帧第二字节猜。
 * 返回两位大写 16 进制；缺省 / 非标准时返回 null。注册的读码与写码见 [READ_FCS] / [WRITE_FCS]。
 */
fun functionFcOf(function: ModbusServiceFunction): String? =
    function.request?.fc?.uppercase()?.takeIf { it.matches(Regex("^[0-9A-F]{2}$")) }

/** 读方法功能码（v2 口径；与后端 `READ_FCS` 一致） */
internal val READ_FCS: Set<String> = setOf("01", "02", "03", "04")

/** 写方法功能码（v2 口径；与后端 `WRITE_FCS` 一致） */
internal val WRITE_FCS: Set<String> = setOf("05", "06", "0F", "10")

/**
 * 方法是否读方法（fc 01/02/03/04）。
 *
 * 只有读方法能挂自动调用周期：写方法的应答是请求回显，周期调用等于让服务端周期性地往寄存器里
 * 写值，后端会直接拒。故「周期 / 轮询」两列对写方法恒为 `-`。
 */
fun isReadFunction(function: ModbusServiceFunction): Boolean =
    functionFcOf(function) in READ_FCS

/** 写方法：应答是请求回显、没有读值，v2 里整个 `response` 键不存在（== null） */
fun isWriteFunction(function: ModbusServiceFunction): Boolean =
    functionFcOf(function) in WRITE_FCS

/** 功能码 → 功能码名称的 string 资源；未知/空值返回 null（对齐 web 的 `fcLabelKey`）。 */
fun fcLabelResOf(fc: String?): Int? = when (fc) {
    "01" -> R.string.modbus_fc_read_coils
    "02" -> R.string.modbus_fc_read_discrete
    "03" -> R.string.modbus_fc_read_holding
    "04" -> R.string.modbus_fc_read_input
    "05" -> R.string.modbus_fc_write_coil
    "06" -> R.string.modbus_fc_write_register
    "0F" -> R.string.modbus_fc_write_coils
    "10" -> R.string.modbus_fc_write_registers
    else -> null
}

/**
 * 请求帧的一行摘要（对齐 web 的 `describeFunctionRequest`）。
 *
 * 读方法：`Slave address 4 · fc 03 Read holding registers · Start address 1 · Quantity 1`；
 * 写方法：… `Quantity 2`（写方法的方向不含「数量」区段，个数由 fields 推出）。
 *
 * `@Composable` 而非纯函数：方向词与标签从资源取（web 是传 `t()`；本文件不认识 i18n 服务，
 * 而这些都是要翻译的界面文案，故直接在组合里取，与 [responseSummary] 同一套做法）。
 * `fc` 那两位 hex 不翻译（与定义里存的值逐字对齐）；分隔符「 · 」（U+00B7）中英通用，是字面量。
 * `describeFunctionRequest` 不组帧、不含 CRC16 —— 帧由后端 invoke 时现组（见 detail 卡片）。
 */
@Composable
fun describeFunctionRequest(function: ModbusServiceFunction): String {
    val request = function.request ?: return "-"
    val fc = functionFcOf(function)
    val slaveLabel = stringResource(R.string.modbus_label_slave_address)
    val startLabel = stringResource(R.string.modbus_field_start_address)
    val quantityLabel = stringResource(R.string.modbus_field_quantity)
    val fcLabel = fc?.let { fcLabelResOf(it) }?.let { stringResource(it) }
    val separator = " · "

    val fcChunk = fc?.let { fcLabel?.let { label -> "fc $it $label" } ?: "fc $it" }
    val base = listOfNotNull(
        request.slaveId?.let { "$slaveLabel $it" },
        fcChunk
    ).joinToString(separator)

    if (isReadFunction(function)) {
        val start = request.start?.let { "$startLabel $it" } ?: return base
        val quantity = request.quantity?.let { "$quantityLabel $it" }
        return listOfNotNull(base, start, quantity).joinToString(separator)
    }
    // 写：个数由 fields 推出，不显示具体区段
    val fields = request.fields?.size ?: 0
    return listOfNotNull(base, "$quantityLabel $fields").joinToString(separator)
}

/**
 * 方法的自动调用周期：没配周期（含全部写方法）= 只手动调用，显示 `-`；
 * 配了就是周期值，**停用（开关关着）时也照常显示** —— 那是留着待用的配置。
 */
fun scheduleLabel(function: ModbusServiceFunction): UiText =
    function.interval?.let {
        // 数量传两次：一次选档位（英文 1 second / 2 seconds），一次填 %1$d
        UiText.Quantity(R.plurals.modbus_service_interval_seconds, it, listOf(it))
    } ?: UiText.Raw("-")

/**
 * 自动轮询状态：启用 / 停用（周期保留）/ `-`（写方法或没配周期）。
 *
 * 定义里没写 `polling` 的按「有周期即启用」算（与后端校验的缺省判定一致）—— 故这里判的是
 * `polling == false` 而不是 `polling == true`：缺省与 true 都算启用。
 */
fun pollingLabel(function: ModbusServiceFunction): UiText {
    if (!isReadFunction(function) || function.interval == null) return UiText.Raw("-")
    val res = if (function.polling == false) {
        R.string.modbus_service_polling_disabled
    } else {
        R.string.modbus_service_polling_enabled
    }
    return UiText.Res(res)
}

/** 轮询处于「停用」态（周期留着、只是暂停）：页面上给它一个弱化的配色 */
fun isPollingOff(function: ModbusServiceFunction): Boolean =
    isReadFunction(function) && function.interval != null && function.polling == false

/**
 * 一条告警规则的一句话：`温度过高(>80)`。
 *
 * 用**符号**而不是「超过」那类词：这是与 `uint16/2B` 摆在一起的技术摘要，符号与定义里存的值
 * 逐字对齐。`threshold` 走 [configNumberText]（**不是** [numberText]）：阈值是用户配的数，
 * 收成 2 位就把 20.125 说成 20.13，而 web 原样显示 —— 那是配置值，得说得出「我配的是多少」。
 */
fun alarmRuleBrief(alarm: ModbusServiceFieldAlarm): String {
    val target = alarm.threshold?.let { configNumberText(it) } ?: alarm.state.orEmpty()
    return "${alarm.text.orEmpty()}(${alarm.compare.orEmpty()}$target)"
}

/**
 * 一个方法配了告警的**出值**（应答字段 + 位清单里各一位），按定义顺序；没配的出值不列。
 *
 * 位是独立的结果键 —— 后端逐位把 0/1 写进返回值，所以位与它的父字段各占一行、各配各的告警；
 * 只挂父字段的话「位 = 1 就告警」根本够不着。
 */
data class ServiceOutputAlarms(
    /** 出值名：invoke 返回值里的 key，也是告警行里的 `field`（**数据、不翻译**） */
    val key: String,
    /** 这个出值的一组规则，按定义顺序（顺序参与运行期的裁决） */
    val alarms: List<ModbusServiceFieldAlarm>
)

fun alarmedOutputs(function: ModbusServiceFunction): List<ServiceOutputAlarms> {
    val outputs = mutableListOf<ServiceOutputAlarms>()
    for (field in function.response?.fields.orEmpty()) {
        val fieldKey = field.field.orEmpty()
        if (field.alarms.isNotEmpty()) outputs += ServiceOutputAlarms(fieldKey, field.alarms)
        for (bit in field.bitList) {
            if (bit.alarms.isNotEmpty()) {
                outputs += ServiceOutputAlarms(bit.field.orEmpty(), bit.alarms)
            }
        }
    }
    return outputs
}

/**
 * 一个方法配了多少条告警规则：全部出值加起来的条数。
 *
 * **停用的规则也算**：数的是「配了几条」，不是「此刻有几条生效」—— 后者随值上下起伏，
 * 不该出现在配置页上。写方法没有出值，恒为 0。
 */
fun definedAlarmCount(function: ModbusServiceFunction): Int =
    alarmedOutputs(function).sumOf { it.alarms.size }

/**
 * 应答字段里的**位清单**（01/02 位区逐位取值）：每行「所属字段 → 位名」。
 *
 * 位是独立的结果键，调用后会与父字段一起出现在返回值里，故要在应答字段区块单独列出
 * —— 光看「整段位掩码」那一行，用户不知道里面还拆出了哪几位。
 */
fun bitListRows(function: ModbusServiceFunction): List<Pair<String, String>> =
    function.response?.fields.orEmpty().flatMap { field ->
        field.bitList.map { bit -> (field.field.orEmpty()) to (bit.field.orEmpty()) }
    }

/**
 * 调用坐标 `#siid · #aiid`：帧发给依赖设备的哪个服务、哪个方法。
 *
 * `argument`（入参 piid）是这三个坐标里唯一的「填帧位置」，一并带上更好排障。
 */
fun coordinateLabel(device: ModbusServiceDevice?): UiText {
    if (device == null) return UiText.Raw("-")
    // 坐标是数字与 id，本身不翻译；要翻译的是那对全角括号（英文该是半角）
    return UiText.Res(
        R.string.modbus_service_coordinate,
        listOf(device.siid?.toString() ?: "-", device.aiid?.toString() ?: "-",
            device.argument?.toString() ?: "-")
    )
}

/**
 * 单个应答字段的规格描述：`格式/字节数 [字节序] [×缩放] [单位]`，
 * 括号里的部分有才出现（对齐 web 的 `describeFunctionResponse`）。
 */
fun fieldSpec(field: ModbusServiceField): String {
    val head = listOfNotNull(
        field.format?.takeIf { it.isNotBlank() },
        field.bytes?.toString()
    ).joinToString("/")

    val extras = mutableListOf<String>()
    field.byteOrder?.takeIf { it.isNotBlank() }?.let { extras += it }
    // scale 为 1（或缺省）等于不缩放，列出来只是噪音
    field.scale?.takeIf { it != 1.0 }?.let { extras += "×$it" }
    field.unit?.takeIf { it.isNotBlank() }?.let { extras += it }

    val tail = if (extras.isEmpty()) "" else extras.joinToString(" ", prefix = " [", postfix = "]")
    return head + tail
}

/**
 * 方法的应答字段摘要（一行读完）；写方法给固定文案。
 *
 * `@Composable` 而非纯函数：字段之间的分隔符中英不同（`，` / `, `），而 join 的结果没法是
 * 一条 `UiText` —— 只能在这儿把语言定死。调用点本来就在组合里，故签名之外没有任何改动。
 */
@Composable
fun responseSummary(function: ModbusServiceFunction): String {
    if (isWriteFunction(function)) return stringResource(R.string.modbus_service_write_hint)
    val separator = stringResource(R.string.modbus_service_field_separator)
    return function.response?.fields.orEmpty().joinToString(separator) { field ->
        "${field.field ?: "-"} ${fieldSpec(field)}"
    }
}

/** 调用结果里某一列的单位：从该方法的应答定义里按字段名回查（值本身后端已经解好了） */
fun unitOf(function: ModbusServiceFunction?, fieldName: String): String? =
    function?.response?.fields?.find { it.field == fieldName }?.unit?.takeIf { it.isNotBlank() }

/**
 * 调用结果里的一个值 → 展示串。
 *
 * **必须处理 Gson 的数值归一**：调用返回的是 `Map<String, Any?>`，声明类型是 Object，
 * Gson 会把**所有** JSON 数字反序列化成 Double，于是 scale=1 的读数会显示成 `1.0`，
 * 而 web 显示 `1`。数值一律交给 [numberText]（整数不带小数点、浮点最多 2 位）—— 与 web 的
 * `formatValue` → `valueText` 逐字对齐：float32 寄存器解出来是 `23.4567890167…`，
 * 两端都只说 `23.46`。
 *
 * 注意 scale 与 value-list **后端已经应用过**（ModbusResponseParser），这里只是格式化，
 * 不要再缩放一次。
 */
fun formatInvokeValue(value: Any?): String = when (value) {
    null -> "-"
    // Float 先抬成 Double 再收：Float 的二进制尾巴更长（23.456789f 的 Double 形是
    // 23.4567890167…），收 2 位正好把两边抹平
    is Double -> numberText(value)
    is Float -> numberText(value.toDouble())
    else -> value.toString()
}

/** 原始 JSON（调试用，对齐 web 结果卡最下面那块 pre） */
fun invokeResultJson(data: Map<String, Any?>): String =
    runCatching { Gson().newBuilder().setPrettyPrinting().create().toJson(data) }
        .getOrDefault("{}")
