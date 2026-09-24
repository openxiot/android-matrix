package cc.openxiot.matrix.data.api

import com.google.gson.annotations.SerializedName

/**
 * Modbus 采集历史：服务端按各方法的 interval 周期自动调用依赖设备，把读到的字段值落库，
 * 本文件对应后端 `/matrix/v1/modbus/history` 的三种返回，字段对齐 webapp-matrix 的
 * `src/app/typedef/define/modbus/ModbusHistory.ts`。
 *
 * 三个接口的分工：
 * - [ModbusHistoryCurrent]：每个方法**最后一次成功采到的值**（服务详情页那种快照，只是多了采集时刻与最近错误）；
 * - [ModbusHistoryRange]：**一个方法的某一个字段**在时间窗内的序列（曲线与表格都取这里）；
 * - [ModbusHistoryFailures]：采集失败明细 + 按类型/远端码的汇总（失败不进上面两份数据）。
 *
 * 时间一律是**毫秒时间戳**（后端 `Date.getTime()`），没有「最近 N 分钟」这种窗口参数，
 * 取数必须自己算 from/to。
 *
 * 可空字段一律给默认值：后端只在有值时下发（web 侧同样到处用 `?.` 兜）。
 */

/**
 * 一个方法的当前状态（`/current` 里的一项）：最后一次**成功**采集的字段值 + 那一刻，
 * 以及最近一次失败（成功过一次后仍是失败态时才有 lastError/errorAt）。
 */
data class ModbusHistoryFunctionState(
    /** 方法序号（对应服务定义里 functions[].index） */
    @SerializedName("functionIndex") val functionIndex: Int = 0,
    /**
     * 字段名 → 最后一次成功采到的值（键即应答字段名，含位区展开出来的位名）。
     *
     * 后端声明成 `Object`，故值是 `Any?`：可能是 Double / String / Boolean。
     * **键与值都是数据，原样显示、不翻译。**
     */
    @SerializedName("fields") val fields: Map<String, Any?> = emptyMap(),
    /** 最后一次成功采集的时刻（毫秒）；一次都没成功过则没有 */
    @SerializedName("recordedAt") val recordedAt: Long? = null,
    /** 最近一次失败的消息；采集正常则没有 */
    @SerializedName("lastError") val lastError: String? = null,
    /** 当前这轮失败**首次**发生的时刻（同一条错误只记第一次） */
    @SerializedName("errorAt") val errorAt: Long? = null
)

/** 整个服务的当前值快照（`GET /history/current/{spaceId}/{serviceId}`） */
data class ModbusHistoryCurrent(
    @SerializedName("serviceId") val serviceId: String = "",
    /** 有采集状态的方法；没配轮询或从未采过的方法不在其中 */
    @SerializedName("functions") val functions: List<ModbusHistoryFunctionState> = emptyList()
)

/**
 * 序列上的一点：**原始样本**或**降采样桶**，由父级 [ModbusHistoryRange.downsampled] 区分。
 *
 * web 侧是两个类型（`ModbusHistorySample` | `ModbusHistoryBucket`）的联合，Kotlin 这边
 * 折成一个把两组字段都收下的 data class —— Gson 无法按内容挑类型，而父级的 `downsampled`
 * 已经把「这批点是哪种形状」说清楚了，读的时候按它分支即可（见 `ModbusHistoryFormat`）。
 */
data class ModbusHistoryPoint(
    /** 采样时刻 / 桶起点（毫秒，含） */
    @SerializedName("at") val at: Long = 0,
    /** 桶终点（毫秒，不含）：与下一个桶的 at 相接，最后一个桶到 `to` 为止。仅桶有 */
    @SerializedName("until") val until: Long? = null,
    /** 桶内原始样本数。仅桶有 */
    @SerializedName("count") val count: Int? = null,
    /** 原始样本的值。**数据、不翻译**（见 [ModbusHistoryRange.points] 的说明）。仅样本有 */
    @SerializedName("value") val value: Any? = null,
    /** true = 值没变、按 keep-alive 时限补记的一条（后端只在为 true 时下发这个键） */
    @SerializedName("keepalive") val keepalive: Boolean? = null,
    /** 桶首尾的**状态值**（可能是字符串）。仅桶有 */
    @SerializedName("first") val first: Any? = null,
    @SerializedName("last") val last: Any? = null,
    /** 桶内数值的最小 / 最大 / 时间加权平均；非数值字段三个统计量都是 null，但键仍然在 */
    @SerializedName("min") val min: Double? = null,
    @SerializedName("max") val max: Double? = null,
    @SerializedName("avg") val avg: Double? = null
)

/**
 * 一个方法的某一个字段在时间窗内的序列
 * （`GET /history/range/{spaceId}?serviceId&functionIndex&field&from&to&maxPoints`）。
 *
 * 注意口径：一条原始采样是**值相对上一次变了**（或到了 keep-alive 时限）才落库的，
 * 故序列是稀疏的，相邻两点的间隔等于「值保持不变的时长」，不代表没在采集。
 */
data class ModbusHistoryRange(
    @SerializedName("serviceId") val serviceId: String = "",
    @SerializedName("functionIndex") val functionIndex: Int = 0,
    /** 字段名。**数据、不翻译** */
    @SerializedName("field") val field: String = "",
    /** 实际生效的窗口（毫秒，左闭右开） */
    @SerializedName("from") val from: Long = 0,
    @SerializedName("to") val to: Long = 0,
    /** 实际生效的最大点数（请求值被后端夹到 [1, 2000]） */
    @SerializedName("maxPoints") val maxPoints: Int = 0,
    /** true = points 是降采样桶；false = 原始样本 */
    @SerializedName("downsampled") val downsampled: Boolean = false,
    /** 窗口内的原始样本总数（两种模式下都有，降采样时大于 points.size） */
    @SerializedName("total") val total: Int = 0,
    @SerializedName("points") val points: List<ModbusHistoryPoint> = emptyList(),
    /**
     * 窗口**之前**最近的一条样本：窗口内第一条的变化基准，用来把曲线从 from 那一刻接上
     * （没有更早的样本时后端整个键都不下发，故这里是 null）。
     */
    @SerializedName("carryIn") val carryIn: ModbusHistoryPoint? = null
)

/**
 * 一条采集失败。同一条消息（message 相同）**只记第一次**，故这列的是「错误首次出现的时刻」，
 * 不是每次失败都有一行 —— 与曲线上的竖线含义一致。
 */
data class ModbusHistoryFailure(
    /**
     * 这条失败属于哪个服务。按服务查时与响应顶层的 `serviceId` 重复；**空间级查询（不传 serviceId）
     * 时是唯一的归属依据** —— 那种查法回来的清单里混着多个服务的失败，清单的名称列得靠它。
     */
    @SerializedName("serviceId") val serviceId: String? = null,
    @SerializedName("functionIndex") val functionIndex: Int = 0,
    /** 失败类型的后端枚举名（`NO_RESPONSE` / `SLAVE_EXCEPTION` / … 之一）；老数据可能没有 */
    @SerializedName("type") val type: String? = null,
    /** 从站异常码 / 依赖设备返回的远端码；只有这两类失败有 */
    @SerializedName("remoteCode") val remoteCode: Int? = null,
    /** 失败消息（服务端下发的原文）。**数据、原样显示、不翻译** */
    @SerializedName("message") val message: String = "",
    /** 首次出现的时刻（毫秒） */
    @SerializedName("at") val at: Long = 0
)

/** 按类型汇总的一项 */
data class ModbusFailureTypeCount(
    @SerializedName("type") val type: String = "",
    @SerializedName("count") val count: Int = 0,
    /** 该类型最近一次出现的时刻 */
    @SerializedName("lastAt") val lastAt: Long = 0
)

/** 按远端码汇总的一项 */
data class ModbusFailureCodeCount(
    @SerializedName("remoteCode") val remoteCode: Int = 0,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("lastAt") val lastAt: Long = 0
)

/** 失败汇总：只统计**本次返回的这批 items**，要连着 truncated 一起读 */
data class ModbusFailureSummary(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("byType") val byType: List<ModbusFailureTypeCount> = emptyList(),
    @SerializedName("byRemoteCode") val byRemoteCode: List<ModbusFailureCodeCount> = emptyList()
)

/**
 * 采集失败清单
 * （`GET /history/failures/{spaceId}?serviceId&functionIndex&type&from&to&limit`）。
 *
 * `serviceId` 不传 = **整个空间**：后端把空间下所有服务的失败合成一条时间倒序的清单，
 * 响应里就没有顶层 `serviceId` 这个键（每条 item 自带一个，见 [ModbusHistoryFailure.serviceId]），
 * 故这里它是可空的。`limit` 与 `truncated` 也跟着变成**整份清单**的口径，而不是某个服务的。
 */
data class ModbusHistoryFailures(
    /** 查的是哪个服务；空间级查询时没有这个键 */
    @SerializedName("serviceId") val serviceId: String? = null,
    @SerializedName("from") val from: Long = 0,
    @SerializedName("to") val to: Long = 0,
    @SerializedName("limit") val limit: Int = 0,
    /** true = 窗口内还有更早的失败没取回来（items 只有最近 limit 条） */
    @SerializedName("truncated") val truncated: Boolean = false,
    /** 时间**倒序**（最新的在前） */
    @SerializedName("items") val items: List<ModbusHistoryFailure> = emptyList(),
    @SerializedName("summary") val summary: ModbusFailureSummary = ModbusFailureSummary()
)
