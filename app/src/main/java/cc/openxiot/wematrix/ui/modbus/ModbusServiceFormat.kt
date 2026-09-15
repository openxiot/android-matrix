package cc.openxiot.wematrix.ui.modbus

import cc.openxiot.wematrix.data.api.ModbusServiceDevice
import cc.openxiot.wematrix.data.api.ModbusServiceField
import cc.openxiot.wematrix.data.api.ModbusServiceFieldAlarm
import cc.openxiot.wematrix.data.api.ModbusServiceFunction
import com.google.gson.Gson

/**
 * Modbus 服务的展示口径，对齐 webapp-matrix 的
 * `pages/main/device/services/service/detail/device.service.detail.component.*`。
 *
 * 与 [ModbusFormat] 分开：那份是「设备点表」的口径（功能码、逻辑地址），这份是「服务」的口径
 * （调用坐标、请求帧、应答字段、调用结果）。
 *
 * 这里只放「纯函数 → 字符串」，配色之类 Compose 类型留在各 Screen 里。
 */

/** 写方法提示（web 同款文案） */
const val WRITE_METHOD_HINT = "写方法：应答为请求回显，没有返回字段（设备已收到该帧）"

/** 写方法调用成功后的简短提示 */
const val WRITE_METHOD_SHORT = "设备已收到该帧"

/**
 * 请求帧里的功能码（两位大写 16 进制）：帧结构 `[slave][fc][...]`，即第二个字节。
 *
 * 帧缺失 / 太短 / 不是 16 进制时返回 null —— 判不出功能码就当「不是读方法」，
 * 与后端从请求帧第二字节判定读写的口径一致（服务定义里不存 fc，只有这条帧）。
 */
fun functionFcOf(request: String?): String? {
    val hex = request.orEmpty().replace(Regex("\\s+"), "")
    if (hex.length < 4) return null
    val fc = hex.substring(2, 4).uppercase()
    return if (fc.matches(Regex("^[0-9A-F]{2}$"))) fc else null
}

/**
 * 方法是否读方法（fc 01/02/03/04）。
 *
 * 只有读方法能挂自动调用周期：写方法的应答是请求回显，周期调用等于让服务端周期性地往寄存器里
 * 写值，后端会直接拒。故「周期 / 轮询」两列对写方法恒为 `-`。
 */
fun isReadFunction(function: ModbusServiceFunction): Boolean =
    functionFcOf(function.request) in setOf("01", "02", "03", "04")

/**
 * 方法的自动调用周期：没配周期（含全部写方法）= 只手动调用，显示 `-`；
 * 配了就是周期值，**停用（开关关着）时也照常显示** —— 那是留着待用的配置。
 */
fun scheduleLabel(function: ModbusServiceFunction): String =
    function.interval?.let { "$it 秒" } ?: "-"

/**
 * 自动轮询状态：启用 / 停用（周期保留）/ `-`（写方法或没配周期）。
 *
 * 定义里没写 `polling` 的按「有周期即启用」算（与后端校验的缺省判定一致）—— 故这里判的是
 * `polling == false` 而不是 `polling == true`：缺省与 true 都算启用。
 */
fun pollingLabel(function: ModbusServiceFunction): String {
    if (!isReadFunction(function) || function.interval == null) return "-"
    return if (function.polling == false) "停用" else "启用"
}

/** 轮询处于「停用」态（周期留着、只是暂停）：页面上给它一个弱化的配色 */
fun isPollingOff(function: ModbusServiceFunction): Boolean =
    isReadFunction(function) && function.interval != null && function.polling == false

/**
 * 一条告警规则的一句话：`温度过高(>80)`。
 *
 * 用**符号**而不是「超过」那类词：这是与 `uint16/2B` 摆在一起的技术摘要，符号与定义里存的值
 * 逐字对齐。`threshold` 走 [numberText]：Gson 把 JSON 数字都解成 Double，80 会显示成 80.0。
 */
fun alarmRuleBrief(alarm: ModbusServiceFieldAlarm): String {
    val target = alarm.threshold?.let { numberText(it) } ?: alarm.state.orEmpty()
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
    for (field in function.response) {
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
    function.response.flatMap { field ->
        field.bitList.map { bit -> (field.field.orEmpty()) to (bit.field.orEmpty()) }
    }

/**
 * 调用坐标 `#siid · #aiid`：帧发给依赖设备的哪个服务、哪个方法。
 *
 * `argument`（入参 piid）是这三个坐标里唯一的「填帧位置」，一并带上更好排障。
 */
fun coordinateLabel(device: ModbusServiceDevice?): String {
    if (device == null) return "-"
    val siid = device.siid?.toString() ?: "-"
    val aiid = device.aiid?.toString() ?: "-"
    val argument = device.argument?.toString() ?: "-"
    return "#$siid · #$aiid（piid $argument）"
}

/** 写方法：应答是请求回显、没有读值，response 为空数组 */
fun isWriteFunction(function: ModbusServiceFunction): Boolean = function.response.isEmpty()

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

/** 方法的应答字段摘要（一行读完）；写方法给固定文案 */
fun responseSummary(function: ModbusServiceFunction): String {
    if (isWriteFunction(function)) return WRITE_METHOD_HINT
    return function.response.joinToString("，") { field ->
        "${field.field ?: "-"} ${fieldSpec(field)}"
    }
}

/** 调用结果里某一列的单位：从该方法的应答定义里按字段名回查（值本身后端已经解好了） */
fun unitOf(function: ModbusServiceFunction?, fieldName: String): String? =
    function?.response?.find { it.field == fieldName }?.unit?.takeIf { it.isNotBlank() }

/**
 * 调用结果里的一个值 → 展示串。
 *
 * **必须处理 Gson 的数值归一**：调用返回的是 `Map<String, Any?>`，声明类型是 Object，
 * Gson 会把**所有** JSON 数字反序列化成 Double，于是 scale=1 的读数会显示成 `1.0`，
 * 而 web 显示 `1`。所以整数值的 Double 走 Long 再 toString。
 *
 * 注意 scale 与 value-list **后端已经应用过**（ModbusResponseParser），这里只是格式化，
 * 不要再缩放一次。
 */
fun formatInvokeValue(value: Any?): String = when (value) {
    null -> "-"
    is Double -> if (value == Math.floor(value) && !value.isInfinite()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
    is Float -> if (value.toDouble() == Math.floor(value.toDouble())) {
        value.toLong().toString()
    } else {
        value.toString()
    }
    else -> value.toString()
}

/** 原始 JSON（调试用，对齐 web 结果卡最下面那块 pre） */
fun invokeResultJson(data: Map<String, Any?>): String =
    runCatching { Gson().newBuilder().setPrettyPrinting().create().toJson(data) }
        .getOrDefault("{}")
