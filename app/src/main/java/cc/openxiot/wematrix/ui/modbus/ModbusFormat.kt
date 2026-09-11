package cc.openxiot.wematrix.ui.modbus

import cc.openxiot.wematrix.data.api.ModbusCommand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设备点表的展示口径，逐条对齐 webapp-matrix 的
 * `pages/main/modbus/command/point.options.ts` 与 `modbus.lifecycle.ts`。
 * 列表页与详情页共用，免得两处说法不一致。
 *
 * 这里只放「纯函数 → 字符串」，配色之类 Compose 类型留在各 Screen 里。
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
