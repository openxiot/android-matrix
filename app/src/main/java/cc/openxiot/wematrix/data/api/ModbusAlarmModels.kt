package cc.openxiot.wematrix.data.api

import com.google.gson.annotations.SerializedName

/**
 * Modbus 阈值告警：读方法的出值越过阈值时服务端记下的一条事件，字段对齐 webapp-matrix 的
 * `src/app/typedef/define/modbus/ModbusAlarm.ts`（后端 `/matrix/v1/modbus/alarm`）。
 *
 * 与采集历史（[ModbusHistoryModels]）的分工：那边记的是**采到什么**，这边记的是**值不对**。
 * 与采集失败（[ModbusHistoryFailure]）也不同：那是「没采到」，这是「采到了但越限」。
 *
 * 三条与其他接口不同的口径：
 * - 每条告警带自己的 `id` —— 它有**逐行动作**（点「处理」写回执），历史行没有；
 * - 一条告警带**触发那一刻的定义快照**（`compare` / `threshold` / `state` / `level` / `text` / `unit`）：
 *   定义是活的、行是不可变的历史，列表里的「触发条件」直接读快照而不是当前点表；
 * - `text` / `field` / `state` / `unit` / `sample` 都是**数据**（用户填的或点表里的），
 *   页面**原样显示、永不翻译**（见 webapp-matrix 的 AGENTS.md）。
 *
 * 时间一律是**毫秒时间戳**（后端 `Date.getTime()`），与其他 Modbus 接口同口径。
 *
 * 可空字段一律给默认值：后端只在有值时下发（web 侧同样到处用 `?.` 兜）。
 */

/**
 * 一条阈值告警（后端 `ModbusServiceAlarm`，集合 `modbus`/`service-alarms`）。
 *
 * 三个时间键的分工：`at` 是**越限首次出现**的时刻（不是每条样本都记），`recoveredAt` 是值回到
 * 正常（或定义不再覆盖这个键）的时刻，`handledBy.timestamp` 是**用户点处理**的时刻。
 * 判定按边沿：持续越限不重复记，回正常后再越限才是新的一条。
 */
data class ModbusAlarm(
    /** 十六进制主键：点「处理」时要把它发回去 */
    @SerializedName("id") val id: String? = null,
    /**
     * 这条告警属于哪个服务。按服务查时与响应顶层的 `serviceId` 重复；**空间级查询（不传 `serviceId`）
     * 时是唯一的归属依据** —— 那种查法回来的清单里混着多个服务的告警，清单的名称列得靠它。
     */
    @SerializedName("serviceId") val serviceId: String? = null,
    /** 方法序号（对应服务定义里 functions[].index） */
    @SerializedName("functionIndex") val functionIndex: Int = 0,
    /**
     * 出值名（字段名，或位清单里那一位的名字）。**数据、不翻译** —— 它与服务定义里的字段名
     * 逐字相同，翻了就对不上了。
     */
    @SerializedName("field") val field: String = "",
    // —— 触发那一刻的定义快照（不是当前点表：定义改了不该改写历史告警的含义）——
    /** 比较方式（`>` `>=` `<` `<=` `=` 之一，线上就是**符号**） */
    @SerializedName("compare") val compare: String? = null,
    @SerializedName("threshold") val threshold: Double? = null,
    /** `=` 的比较目标：取值表的 description */
    @SerializedName("state") val state: String? = null,
    /** `INFO` | `WARN` | `CRITICAL` 之一 */
    @SerializedName("level") val level: String? = null,
    /** 告警文本（用户自己填的，如「温度过高」）—— **用户数据，原样显示、永不翻译** */
    @SerializedName("text") val text: String? = null,
    /** 单位（点表里的数据、不翻译）；位上的告警没有单位 */
    @SerializedName("unit") val unit: String? = null,
    // —— 边沿 ——
    /** 越限首次出现的时刻（毫秒） */
    @SerializedName("at") val at: Long = 0,
    /** 值回到正常的时刻（毫秒）；**没有这个键 = 仍在越限**（这就是状态本身） */
    @SerializedName("recoveredAt") val recoveredAt: Long? = null,
    /**
     * 怎么关掉的（`VALUE` / `DEFINITION` / `SUPERSEDED` 之一，页面照级别那样给标签）。
     *
     * 它存在的意义是让「一条没有恢复样本的关闭」可解释 —— 否则用户只会看到一条告警莫名其妙地
     * 变成了「已恢复」。而三条路径必须都露脸：只显示其中一种，另外两种的行就说了半句话。
     */
    @SerializedName("closeType") val closeType: String? = null,
    // —— 处理 ——
    /** true = 已处理（用户点过「处理」）；缺省 / false 都是未处理 */
    @SerializedName("handled") val handled: Boolean? = null,
    /** 处理人与处理时刻（取 `timestamp`）；重复点击不会把第一个处理的人顶掉 */
    @SerializedName("handledBy") val handledBy: ModbusPerson? = null,
    // —— 触发时的样本 ——
    /**
     * 越限那一刻的值（数值，或取值表的 description 字符串）；**开着期间不刷新**，
     * 所以它是「当时为什么报」而不是「现在多少」。**数据、不翻译。**
     *
     * 后端声明成 `Object`，故这里是 `Any?`：可能是 Double、String 或 null，
     * 展示前用 [cc.openxiot.wematrix.ui.modbus.asDoubleOrNull] 之类的分支处理。
     */
    @SerializedName("sample") val sample: Any? = null
)

/** 按级别汇总的一项（`level` 是后端枚举名，页面用标签映射函数给中文） */
data class ModbusAlarmLevelCount(
    @SerializedName("level") val level: String = "",
    @SerializedName("count") val count: Int = 0,
    /** 该级别最近一次告警的时刻（毫秒）；没有可用的时刻时后端不下发这个键 */
    @SerializedName("lastAt") val lastAt: Long? = null
)

/** 按告警文本汇总的一项：`text` 是**用户数据**，原样显示 */
data class ModbusAlarmTextCount(
    @SerializedName("text") val text: String = "",
    @SerializedName("count") val count: Int = 0,
    @SerializedName("lastAt") val lastAt: Long? = null
)

/**
 * 告警汇总：只统计**本次返回的这批 items**，要连着 `truncated` 一起读。
 *
 * `open` 按 `recoveredAt` 有没有来数，`unhandled` 按 `handled` 是不是 true 来数 ——
 * 两个都是「页面同一批行上数得出来」的口径，故此处的数字与清单里看到的一致。
 *
 * `level` / `text` 缺失的行归到一个 `UNKNOWN` 桶里（后端的行为）：那只可能是更早的口径或脏数据，
 * 归桶是为了让计数与 `total` 对得上，**不是替用户编一个告警文本**。
 */
data class ModbusAlarmSummary(
    /** 本次聚合了多少条（= items.size，方便与 truncated 一起读） */
    @SerializedName("total") val total: Int = 0,
    /** 其中仍未恢复的条数 */
    @SerializedName("open") val open: Int = 0,
    /** 其中仍未处理的条数 */
    @SerializedName("unhandled") val unhandled: Int = 0,
    /** 按级别分布（**按条数降序、同数按级别名升序**，后端已排好） */
    @SerializedName("byLevel") val byLevel: List<ModbusAlarmLevelCount> = emptyList(),
    /** 按告警文本分布（「告警类型分布」，同上排序口径） */
    @SerializedName("byText") val byText: List<ModbusAlarmTextCount> = emptyList()
)

/**
 * 告警清单（`GET /alarm/many/{spaceId}?serviceId&functionIndex&field&level&open&handled&from&to&limit`）。
 *
 * `serviceId` 不传 = **整个空间（含子空间）**：后端把空间下所有服务的告警合成一条时间倒序的清单，
 * 响应里就没有顶层 `serviceId` 这个键（每条 item 自带一个，见 [ModbusAlarm.serviceId]），
 * 故这里它是可空的。`limit` 与 `truncated` 也跟着变成**整份清单**的口径，而不是某个服务的。
 */
data class ModbusAlarmList(
    /** 查的是哪个服务；空间级查询时没有这个键 */
    @SerializedName("serviceId") val serviceId: String? = null,
    @SerializedName("from") val from: Long = 0,
    @SerializedName("to") val to: Long = 0,
    /** 实际生效的条数上限（后端夹到 [1, 1000]，缺省 200） */
    @SerializedName("limit") val limit: Int = 0,
    /** true = 窗口内还有更早的告警没取回来（items 只有最近 limit 条），页面该提示缩小时间范围 */
    @SerializedName("truncated") val truncated: Boolean = false,
    /** 时间**倒序**（最新的在前） */
    @SerializedName("items") val items: List<ModbusAlarm> = emptyList(),
    @SerializedName("summary") val summary: ModbusAlarmSummary = ModbusAlarmSummary()
)

/**
 * 告警清单的筛选条件，与接口的查询参数一一对应（**不传 = 不限**）。
 *
 * 它不是线上载荷、不参与 Gson 解析，是给调用方用的参数袋 —— 因为告警有六个筛选，
 * 位置参数排到第六七个就没人记得住顺序了（web 侧同样收在一个对象里）。
 *
 * `open` / `handled` 用 `Boolean?` 而不是 `Boolean`：它们的「不传」与 `false` 必须区分开
 * ——「只看已恢复」不等于「不限」，用 `false` 表达「不传」就永远查不了已恢复的那些。
 */
data class ModbusAlarmQuery(
    /** 不传 = 整个空间（含子空间） */
    val serviceId: String? = null,
    val functionIndex: Int? = null,
    /** 出值名（字段名或位名）。**数据、不翻译** */
    val field: String? = null,
    /** `INFO` / `WARN` / `CRITICAL` 之一；不传 = 所有级别 */
    val level: String? = null,
    /** true 只看未恢复 / false 只看已恢复 / 不传 = 不限 */
    val open: Boolean? = null,
    /** true 只看未处理 / false 只看已处理 / 不传 = 不限 */
    val handled: Boolean? = null,
    /** 条数上限；不传 = 后端缺省（200），上不封顶到 1000 */
    val limit: Int? = null
)
