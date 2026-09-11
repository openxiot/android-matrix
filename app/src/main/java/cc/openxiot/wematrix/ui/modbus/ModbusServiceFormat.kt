package cc.openxiot.wematrix.ui.modbus

import cc.openxiot.wematrix.data.api.ModbusServiceDevice
import cc.openxiot.wematrix.data.api.ModbusServiceField
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
