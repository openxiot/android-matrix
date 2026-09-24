package cc.openxiot.matrix.ui.modbus

import androidx.annotation.StringRes
import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.api.ModbusHistoryPoint
import cc.openxiot.matrix.data.api.ModbusHistoryRange
import cc.openxiot.matrix.ui.core.UiText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Modbus 采集历史的展示口径，逐条对齐 webapp-matrix 的
 * `src/app/typedef/define/modbus/ModbusHistory.ts` 与两个历史页
 * （`pages/main/history/`、`device/services/service/history/`）。
 *
 * 一条硬规矩：**来自服务端的文本一律原样显示、永不翻译** —— 字段名、单位、取值表的描述串、
 * 失败消息都在其列。本文件只把**后端枚举名**（失败类型）换成页面自己的词，
 * 且未收录的枚举名一律原样给出。
 *
 * 时间窗口与降采样桶的口径由**三个页面共用**（告警页 / 项目级历史 / 单服务历史）：
 * web 侧是三份各写一遍，Android 收在这一处，免得日后改一档要改三遍。
 *
 * 这里只放「纯函数」，配色之类 Compose 类型留在各 Screen 里。文案一律返回 [UiText] 或
 * `@StringRes Int`，语言由调用方注入。
 */

/**
 * 时间范围预设：三档「最近 N」+ 自定义给绝对时刻。
 *
 * 与 web 三个页面同一个口径。`to` 一律取当下（自定义除外），`from` 由 [spanMillis] 往前推。
 *
 * 标签给**资源 id**（规则 A：1:1 的固定标签）—— 枚举是纯 Kotlin，没有 Context 也不该有。
 */
enum class RangePreset(val spanMillis: Long, @StringRes val labelRes: Int) {
    HOUR_1(3600L * 1000, R.string.modbus_range_hour1),
    HOUR_24(24 * 3600L * 1000, R.string.modbus_range_hour24),
    DAY_7(7 * 24 * 3600L * 1000, R.string.modbus_range_day7),
    CUSTOM(0, R.string.modbus_range_custom);

    /** 预设档才推得出窗口；自定义由用户给的两个绝对时刻决定 */
    val isPreset: Boolean get() = this != CUSTOM
}

/** 预设档的 `[now - 跨度, now]`；自定义档返回 null（窗口由用户选的两个时刻决定） */
fun presetWindow(preset: RangePreset, now: Long): Pair<Long, Long>? {
    if (!preset.isPreset) return null
    return (now - preset.spanMillis) to now
}

/** 三个预设档，按页面下拉的顺序 */
val rangePresets: List<RangePreset> = RangePreset.entries.toList()

/**
 * 采集失败的类型（后端 `ModbusFailureType` 的枚举名，线上就是这些字符串）。
 *
 * 页面按它给下拉/汇总排序，不另排一遍。
 */
val failureTypes: List<String> = listOf(
    "NO_RESPONSE",
    "SLAVE_EXCEPTION",
    "DEVICE_ERROR",
    "CRC_MISMATCH",
    "FRAME_MISMATCH",
    "INVALID_FRAME",
    "FIELD_DEFINITION_ERROR",
    "CONFIG_ERROR",
    "TRANSPORT_ERROR",
    "UNKNOWN"
)

/**
 * 枚举名 → 界面标签。
 *
 * 兜底那类用的是「未知失败」而不是词典里现成的「未定义」：后者会让人以为「少配了一处定义」
 * 而去翻服务定义，可它其实只是「没归入以上任何一类」。宁可说不知道，也不给一个把人引偏的分类。
 *
 * `remoteCode` 不在这里翻：只有从站异常应答那个码是 Modbus 异常码（1/2/3/4…），
 * 依赖设备报错的远端码是 DTU 厂商自己的状态码，我们并不知道它的含义，猜着翻反而会误导排查。
 */
private val FAILURE_LABELS: Map<String, Int> = mapOf(
    "NO_RESPONSE" to R.string.modbus_failure_no_response,
    "SLAVE_EXCEPTION" to R.string.modbus_failure_slave_exception,
    "DEVICE_ERROR" to R.string.modbus_failure_device_error,
    "CRC_MISMATCH" to R.string.modbus_failure_crc_mismatch,
    "FRAME_MISMATCH" to R.string.modbus_failure_frame_mismatch,
    "INVALID_FRAME" to R.string.modbus_failure_invalid_frame,
    "FIELD_DEFINITION_ERROR" to R.string.modbus_failure_field_definition,
    "CONFIG_ERROR" to R.string.modbus_failure_config,
    "TRANSPORT_ERROR" to R.string.modbus_failure_transport,
    "UNKNOWN" to R.string.modbus_failure_unknown
)

/**
 * 失败类型（+ 远端码）→ 界面标签：有远端码就缀在后面（`异常应答 (2)`）。
 *
 * 枚举名本身不在这里露脸，页面各按各的位置附上 —— 排查时要拿它去搜后端日志，得留在明面上，
 * 但那是版式的事。`type` 缺失（老数据可能没有）给 `-`；没收录的枚举名原样给出。
 */
fun failureLabel(type: String?, remoteCode: Int?): UiText {
    if (type.isNullOrEmpty()) return UiText.Raw("-")
    // 没收录的枚举名原样给出 —— 那是后端枚举名原文，给不了资源
    val label = FAILURE_LABELS[type]?.let { UiText.Res(it) } ?: UiText.Raw(type)
    if (remoteCode == null) return label
    // 后缀本身不含词（`%1$s (%2$d)` 两侧逐字相同，故 StringsParityTest 按「没东西可翻」放行），
    // 但它得套在一条还没定语言的 label 外面，故仍是一条资源、label 作为参数嵌进来
    return UiText.Res(R.string.modbus_failure_with_code, listOf(label, remoteCode))
}

/**
 * 这个点是不是降采样桶。
 *
 * 与 web 的 `isBucket` 同判据（有没有 `until` 这个键）—— 序列的两种形态由它区分，
 * 而不是靠父级的 `downsampled`：类型判断跟着点本身走，函数才不必再要一个上下文参数。
 */
fun isBucket(point: ModbusHistoryPoint): Boolean = point.until != null

/**
 * 原始样本的数值：非数值（取值表的描述串、null）给 null，曲线上留成断点。
 */
fun sampleNumeric(point: ModbusHistoryPoint): Double? = asDoubleOrNull(point.value)

/**
 * 桶的均值：非数值字段的统计量全是 null，那这一桶就画不出来。
 */
fun bucketNumeric(point: ModbusHistoryPoint): Double? = point.avg

/**
 * 降采样桶的展示文案：有统计量（数值字段）时给「均值 (最小 ~ 最大)」，与曲线图上
 * 「实线 + 两条虚线」是同三个数；非数值字段没有统计量，退回桶首尾的状态值。
 */
fun bucketText(point: ModbusHistoryPoint): String {
    val avg = point.avg
    if (avg != null) {
        val rangeText = if (point.min != null && point.max != null) {
            " (${numberText(point.min)} ~ ${numberText(point.max)})"
        } else {
            ""
        }
        return "${numberText(avg)}$rangeText"
    }
    val first = valueText(point.first)
    val last = valueText(point.last)
    return if (first == last) first else "$first ~ $last"
}

/** 一行采样/桶的值文案：桶走 [bucketText]，样本走 [valueText] */
fun historyPointText(point: ModbusHistoryPoint): String =
    if (isBucket(point)) bucketText(point) else valueText(point.value)

/**
 * 采集时刻：桶写成「起点 ~ 终点」，跨天时终点写全，同一天只写时分秒。
 *
 * 库里存的是**毫秒**时间戳，故不能拿 DeviceDetailScreen 那个解析 ISO 串的函数来用。
 */
fun historyTimeText(at: Long, until: Long?): String {
    val head = dateTime(at)
    if (until == null || until == at) return head
    val tail = dateTime(until)
    return "$head ~ ${if (sameDay(at, until)) tail.substring(11) else tail}"
}

/**
 * 窗口之前那条 `carryIn` 的数值：把它补在 `from` 那一刻，曲线才不会从左边缘凭空缺一截
 * （看起来像「那段时间没采到」）。取不到数值时返回 null，曲线就照旧从窗口内第一条开始。
 */
fun carryInNumeric(range: ModbusHistoryRange): Double? = range.carryIn?.let { sampleNumeric(it) }

private fun dateTime(at: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(at))

/**
 * epoch 毫秒 → `yyyy-MM-dd`（只到天）。筛选条上回显「自定义查的是哪两天」用它 ——
 * 窗口是按整天展开的，把 `23:59:59.999` 那半秒也显示出来只会让人以为窗口歪了。
 */
fun epochDate(at: Long?): String {
    if (at == null || at <= 0) return "-"
    return formatEpochMillis(at).substring(0, 10)
}

/**
 * epoch 毫秒 → `MM-dd HH:mm:ss`（**省掉年份**）。
 *
 * 告警与服务历史两张清单里，这一列旁边还有别的字要放，年份（而且几乎总是今年）最不值钱 ——
 * 与 web 两张表的 `date: 'MM-dd HH:mm:ss'` 同口径。
 */
fun epochShort(at: Long?): String {
    if (at == null || at <= 0) return "-"
    return formatEpochMillis(at).substring(5)
}

/**
 * 曲线图横轴刻度的文案：**按窗口跨度选粒度**，与 web 的 `axisTime` 同口径。
 *
 * 一小时的窗口里 `HH:mm:ss` 才看得出节奏，七天的窗口里到分钟就够、再细只会把刻度挤成一团。
 */
fun axisTimeText(at: Long, spanMillis: Long): String {
    val pattern = when {
        spanMillis <= 6 * 3600L * 1000 -> "HH:mm:ss"
        spanMillis <= 3 * 24 * 3600L * 1000 -> "HH:mm"
        else -> "MM-dd HH:mm"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(at))
}

private fun sameDay(a: Long, b: Long): Boolean {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = a
    val dayA = calendar.get(Calendar.YEAR) to calendar.get(Calendar.DAY_OF_YEAR)
    calendar.timeInMillis = b
    val dayB = calendar.get(Calendar.YEAR) to calendar.get(Calendar.DAY_OF_YEAR)
    return dayA == dayB
}
