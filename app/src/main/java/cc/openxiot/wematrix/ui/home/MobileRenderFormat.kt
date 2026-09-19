package cc.openxiot.wematrix.ui.home

import com.google.gson.Gson
import cc.openxiot.wematrix.data.api.ModbusHistoryRange

/**
 * 从一张卡的取数 `data`（`Map<String, Any?>`，线格式如此）里**防御性读出**具体形状。
 *
 * 为什么这一步单独写：`data` 是异构桶，库里可能存着旧版本写的配置、取数可能是个空对象，
 * 防御性读取总比整页报错好。**三条铁律**（与 web 的 `WebDashboardWidgetData.ts` 同口径）：
 * - 数字只认真数字，不收字符串数字（`"12"` 收下来只会掩盖后端一次改动）；
 * - `0` / `''` / `false` 都是**有效读数**，只有「键缺失」才是没值 —— 判 `in`，不看真假；
 * - 一个坏点不该让整张图消失，列表逐项校验、坏项跳过。
 */
object MobileRenderFormat {

    /** 读数字（Long）；字符串数字一律不要。 */
    fun numberOf(map: Map<String, Any?>, key: String): Long? =
        (map[key] as? Number)?.toLong()

    fun doubleOf(map: Map<String, Any?>, key: String): Double? =
        (map[key] as? Number)?.toDouble()

    fun stringOf(map: Map<String, Any?>, key: String): String? =
        map[key] as? String

    fun booleanOf(map: Map<String, Any?>, key: String): Boolean =
        map[key] == true

    /** 整点桶（stat 的 hourly / line 的 points）：`[{at, count}]`，逐项校验。 */
    data class Bucket(val at: Long, val count: Int)

    fun bucketsOf(map: Map<String, Any?>, key: String): List<Bucket> {
        val raw = map[key]
        if (raw !is List<*>) return emptyList()
        val out = ArrayList<Bucket>(raw.size)
        for (entry in raw) {
            if (entry !is Map<*, *>) continue
            @Suppress("UNCHECKED_CAST")
            val m = entry as Map<String, Any?>
            val at = numberOf(m, "at") ?: continue
            val count = (m["count"] as? Number)?.toInt() ?: continue
            out += Bucket(at, count)
        }
        return out
    }

    /** 分布的片（distribution 的 groups）：`[{key, count}]`，键名是数据、原样显示。 */
    data class Slice(val name: String, val count: Int)

    fun slicesOf(map: Map<String, Any?>, key: String): List<Slice> {
        val raw = map[key]
        if (raw !is List<*>) return emptyList()
        val out = ArrayList<Slice>(raw.size)
        for (entry in raw) {
            if (entry !is Map<*, *>) continue
            @Suppress("UNCHECKED_CAST")
            val m = entry as Map<String, Any?>
            val name = m["key"] as? String ?: continue
            val count = (m["count"] as? Number)?.toInt() ?: continue
            out += Slice(name, count)
        }
        return out
    }

    /** 服务卡的一行：字段名 + 值 + 单位 + 是否位。`value` 缺键 = 没取到（显示 `-`）。 */
    data class ServiceRow(
        val field: String,
        val hasValue: Boolean,
        val value: Any?,
        val unit: String,
        val bit: Boolean
    )

    fun serviceRowsOf(map: Map<String, Any?>, key: String): List<ServiceRow> {
        val raw = map[key]
        if (raw !is List<*>) return emptyList()
        val out = ArrayList<ServiceRow>(raw.size)
        for (entry in raw) {
            if (entry !is Map<*, *>) continue
            @Suppress("UNCHECKED_CAST")
            val m = entry as Map<String, Any?>
            val field = m["field"] as? String
            if (field.isNullOrBlank()) continue
            out += ServiceRow(
                field = field,
                hasValue = m.containsKey("value"),
                value = m["value"],
                unit = m["unit"] as? String ?: "",
                bit = m["bit"] == true
            )
        }
        return out
    }

    /**
     * serviceField 曲线：render 的 `data` **本身就是**一条字段序列（serviceId/functionIndex/field/
     * from/to/maxPoints/downsampled/points/unit/step/failures），按 [ModbusHistoryRange] 的形状
     * 解出来，交给共享的历史字段曲线绘制（[HistoryFieldChart]）。unit/step/failures 是额外的、
     * ModbusHistoryRange 没有的键，单独读。
     */
    fun gsonRange(data: Map<String, Any?>): ModbusHistoryRange =
        runCatching { gson.fromJson(gson.toJson(data), ModbusHistoryRange::class.java) }
            .getOrNull() ?: ModbusHistoryRange()

    /** 服务端回显的窗口终点；取不到退回 0，调用方自行兜底。 */
    fun to(map: Map<String, Any?>): Long = numberOf(map, "to") ?: 0

    private val gson by lazy { Gson() }
}