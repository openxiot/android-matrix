package cc.openxiot.wematrix.ui.modbus

import cc.openxiot.wematrix.data.api.ModbusCommand
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设备点表的展示口径，逐条对齐 webapp-matrix 的
 * `pages/main/modbus/command/point.options.ts` 与 `modbus.lifecycle.ts`。
 * 列表页与详情页共用，免得两处说法不一致。
 *
 * 这里只放「纯函数 → 字符串」，配色之类 Compose 类型留在各 Screen 里。
 *
 * 文件末尾三个**数值文案**函数（[valueText] / [numberText] / [configNumberText]）是所有
 * 读数与配置值共用的口径，对齐 web 的 `typedef/utils/ValueUtils.ts`：同一个
 * `0.30000000000000004` 在告警页、两个历史页与看板卡片上必须长得一样，各写一份迟早会走样
 * —— 故与点表口径放在同一处。
 */

/** 功能码 → 中文名（point.options.ts 的 FC_OPTIONS） */
fun fcLabel(fc: String?): String = when (fc) {
    "01" -> "读线圈"
    "02" -> "读离散输入"
    "03" -> "读保持寄存器"
    "04" -> "读输入寄存器"
    "05" -> "写单个线圈"
    "06" -> "写单个寄存器"
    "0F" -> "写多个线圈"
    "10" -> "写多个寄存器"
    else -> "-"
}

/** 写功能码（05/06/0F/10）：读功能码为 01–04，写动作在 web 列表里标红 */
fun isWriteFc(fc: String?): Boolean = fc == "05" || fc == "06" || fc == "0F" || fc == "10"

/**
 * 逻辑地址（1 基工程号）= start + 区段基址；fc 或 start 为空则无。
 * 区段基址：线圈 00001 / 离散输入 10001 / 输入寄存器 30001 / 保持寄存器 40001。
 */
fun logicalAddressOf(fc: String?, start: Int?): Int? {
    val base = when (fc) {
        "01", "05", "0F" -> 1
        "02" -> 10001
        "04" -> 30001
        "03", "06", "10" -> 40001
        else -> return null
    }
    return start?.let { base + it }
}

/** 可见度：private 私有 / public 公开 */
fun visibilityLabel(visibility: String?): String = when (visibility) {
    "public" -> "公开"
    "private" -> "私有"
    else -> "-"
}

/** 生命周期文案；缺省按「开发中」展示（与 web 列表 `config.lifecycle ?? 'development'` 同口径） */
fun lifecycleLabel(lifecycle: String?): String = when (lifecycle ?: "development") {
    "development" -> "开发中"
    "preview" -> "预览"
    "released" -> "已发布"
    else -> "未定义"
}

/** 线圈状态：on/off → ON/OFF */
fun coilStateLabel(state: String?): String = when (state) {
    "on" -> "ON"
    "off" -> "OFF"
    else -> "-"
}

/**
 * 「写内容」列：同一列按功能码显示不同的东西（照 web 编辑器表格）。
 * 读功能码（01–04）没有写内容，恒为 `-`。
 */
fun writeContentLabel(command: ModbusCommand): String = when (command.fc) {
    "05" -> coilStateLabel(command.coilState)
    "06" -> command.registerValue?.toString() ?: "-"
    "0F" -> "${command.coils.size} 个线圈"
    "10" -> "${command.registers.size} 个寄存器"
    else -> "-"
}

/**
 * 01/02 的**逐位命名**：`位偏移 → 位名称`，只列真正命名了的位。
 *
 * 走 [fitBitNames] 而不是直接读 `bitNames`：后者可能带着用户已经删掉、或数量改小后超出范围的
 * 残名，而落库的是裁剪后的那份（见 PointOptions）。
 */
fun bitNameRows(command: ModbusCommand): List<Pair<Int, String>> =
    fitBitNames(command.bitNames, expectedBitCount(command.fc, command.quantity))
        .map { (it.offset ?: 0) to it.name.orEmpty() }

/**
 * 03/04 的应答字段名：按数量的口径补齐到该有的个数（用户没填的用默认名兜底），
 * 与生成服务时 `response[].field` 同一套。
 */
fun fieldNameRows(command: ModbusCommand): List<String> = fitFieldNames(
    command.fieldNames,
    expectedFieldCount(command.fc, command.quantity, command.dataType),
    fieldBaseName(command.name)
)

/**
 * 详情页每条功能码要展示的字段（标签 → 值）。
 *
 * 按功能码分组出现，用不到的字段**不显示**（而不是显示成 `-`）—— 这是 Modbus 的固有形状，
 * 与 web 编辑器表格里那一堆 `-` 相比，手机上这样更省地方也更好读。
 */
fun commandFields(command: ModbusCommand): List<Pair<String, String>> {
    val fields = mutableListOf(
        "起始地址" to (command.start?.toString() ?: "-"),
        "逻辑地址" to (logicalAddressOf(command.fc, command.start)?.toString() ?: "-")
    )
    when (command.fc) {
        // 读位 / 读寄存器：数量 = 该参数占用的寄存器或位个数
        "01", "02", "03", "04" -> fields += "数量" to (command.quantity?.toString() ?: "-")
    }
    when (command.fc) {
        "03", "04" -> {
            fields += "数据格式" to (command.dataType ?: "-")
            fields += "字节序" to (command.byteOrder ?: "-")
            fields += "缩放" to (command.scale?.toString() ?: "-")
            fields += "单位" to (command.unit ?: "-")
        }
        "05", "06", "0F", "10" -> fields += "写内容" to writeContentLabel(command)
    }
    return fields
}

/**
 * epoch **毫秒** → `yyyy-MM-dd HH:mm:ss`。
 * 注意别照抄 DeviceDetailScreen 的 formatUtcToLocal —— 那个解析的是 ISO 字符串，
 * 这里的 timestamp 是毫秒数。
 */
fun formatEpochMillis(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0) return "-"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

/**
 * 采样值的展示文案：数值走 [numberText]（最多 2 位小数），null 显示 `-`。
 *
 * 「值」在协议里可以是数值（含缩放后的浮点）、取值表的描述字符串、位区的 0/1，
 * 故按运行时类型分支，而不是假定是数字。**数据、原样显示、不翻译。**
 */
fun valueText(value: Any?): String = when (value) {
    null -> "-"
    is Number -> numberText(value.toDouble())
    // Gson 把 JSON 对象解成 LinkedTreeMap、数组解成 List，都没有好看的 toString
    is Map<*, *>, is List<*> -> value.toString()
    else -> value.toString()
}

/**
 * 数值文案（**读数**口径）：整数不带小数点，浮点**最多留 2 位小数**
 * （`0.30000000000000004` → `0.3`，`23.4567890` → `23.46`）。
 *
 * 与 web 的 `Number(value.toFixed(2))` 同口径 —— 那边是先判整数再 `toFixed`、这边先收再判，
 * 边界上落点一致（`1.005` 两边都是 `1`）。**「最多」是字面意思**：`23.5` 还是 `23.5`，
 * 不补成 `23.50`；代价是小于 `0.005` 的读数会显示成 `0`，那是「留 2 位」的题中之义。
 *
 * **配置值不走这里**：告警阈值、缩放倍数是用户敲进去的定义，收成 2 位就会把 20.125 显示成
 * 20.13，而 web 那边原样显示 20.125 —— 两边对不上。那些走 [configNumberText]。
 *
 * `1e15` 以上不再收尾数（乘以 100 会溢出 Long），直接交给 Kotlin 自己的格式化 ——
 * Modbus 寄存器解出来的值到不了那个量级。
 */
fun numberText(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return value.toString()
    if (value == Math.floor(value) && Math.abs(value) < 1e15) return value.toLong().toString()
    if (Math.abs(value) >= 1e15) return value.toString()
    // `BigDecimal(value)` 是**二进制原值**（不是 `valueOf` 那条最短十进制串），这一条就是要害：
    // `2.675` 的双精度其实是 `2.67499999…`，收 2 位得 `2.67` —— **与 web 的 `toFixed(2)` 同落点**。
    // 换成 `Math.round(value * 100) / 100` 会先乘出个正好等于 `267.5` 的中间值，那里两边就分家了。
    val rounded = BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
    // 归零单独给 `0`：`stripTrailingZeros` 遇上零在旧 JDK 上不生效，会漏出个 `0.00`
    return if (rounded.compareTo(BigDecimal.ZERO) == 0) "0" else rounded.toPlainString()
}

/**
 * 数值文案（**配置值**口径）：整数不带小数点，浮点收到 4 位。
 *
 * 只给「用户配的数」用：告警阈值（[alarmRuleBrief] / [alarmCondition]）与缩放倍数
 * （见 RequestFrame 的 `×…`）。与 web 的裸 `${threshold}` / `×${scale}` 对齐 —— Gson 把 JSON
 * 数字全解成 Double，1 会显示成 `1.0` 而 web 是 `1`，这里的 4 位只是**压掉浮点噪声**，
 * 不是「显示到 4 位」的意思。读数一律走 [numberText]。
 */
fun configNumberText(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return value.toString()
    if (value == Math.floor(value) && Math.abs(value) < 1e15) return value.toLong().toString()
    if (Math.abs(value) >= 1e15) return value.toString()
    return (Math.round(value * 10000.0) / 10000.0).toString()
}

/**
 * 把任意 JSON 值收成 Double：只有数值本身，以及能整串解析成数值的字符串才算。
 * 取值表的描述（如「制冷」）会落到 null —— 调用方据此走「非数值」分支。
 */
fun asDoubleOrNull(value: Any?): Double? = when (value) {
    null -> null
    is Number -> value.toDouble()
    is String -> value.trim().toDoubleOrNull()
    else -> null
}

