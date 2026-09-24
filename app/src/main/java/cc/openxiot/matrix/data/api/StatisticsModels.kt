package cc.openxiot.matrix.data.api

import com.google.gson.annotations.SerializedName

/**
 * 数据看板（首页）的聚合数字（`GET /matrix/v1/statistics/overview/{spaceId}`），
 * 字段对齐 webapp-matrix 的 `src/app/typedef/define/statistics/OverviewStatistics.ts`。
 *
 * **为什么是一个接口而不是本端拼**：首页一屏要设备、服务、告警、故障四类数字，而现成的清单接口
 * 都带条数上限（告警缺省 200 / 封顶 1000、故障 1000），拿它们算出来的是**被截断的下限**
 * —— 卡片和曲线上完全看不出来。本接口不打上限，量级由窗口上限（31 天）兜住。
 *
 * 三条口径：
 * - `hourly` 是**整点桶且窗口内每个桶都在**（没发生的整点给 0），前端不必猜「这段是没数据还是没请求」；
 * - `total` 都是**整个窗口内**的条数，「今日」由前端从 `hourly` 里取桶起点在今天 00:00 之后的相加；
 * - `type` / `configId` / `text` 全是**数据**（URN 段、点表 id、用户填的告警文本），原样显示、**永不翻译**。
 *
 * 时间一律是**毫秒时间戳**（后端 `Date.getTime()`），与其他接口同口径。
 *
 * 可空字段一律给默认值：后端只在有值时下发（web 侧同样到处用 `?.` 兜）。
 */

/** 一个分组键的条数（键名按分组不同分别是 `type` / `configId` / `text`，见各自的数组） */
data class StatisticsCount(
    /** 分组键。**数据，原样显示** —— 设备类型段 / 点表 id / 用户填的告警文本 */
    @SerializedName("key") val key: String = "",
    @SerializedName("count") val count: Int = 0
)

/** 一个整点桶：`at` 是桶**起点**的毫秒时间戳，`count` 是该小时内的条数（可能是 0） */
data class StatisticsBucket(
    @SerializedName("at") val at: Long = 0,
    @SerializedName("count") val count: Int = 0
)

/** 设备：总数、在线数、按类型（URN 第 4 段，如 `dtu`）的分布 */
data class DeviceOverview(
    @SerializedName("total") val total: Int = 0,
    /** 在线设备的台数（后端按 `device.online` 数） */
    @SerializedName("online") val online: Int = 0,
    /** 按类型的分布，**按条数降序、同数按类型名升序**（后端已排好） */
    @SerializedName("byType") val byType: List<StatisticsCount> = emptyList()
)

/**
 * 服务：总数、按所配点表的分布。
 *
 * 分组键是 `configId`（**不是**显示名）：拼「厂家 型号」是客户端的事（见 [ModbusConfig.displayName]），
 * 首页那一饼的片名因此与服务清单页的「点表名称」列同源。没配点表的服务落在**空串**键上，
 * 页面把它归到「未定义」。
 */
data class ServiceOverview(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("byConfig") val byConfig: List<StatisticsCount> = emptyList()
)

/** 告警：窗口内的总数、按告警文本的分布、按整点的时间线 */
data class AlarmOverview(
    /** 窗口内的告警条数（「今日告警」是它的一部分，见 [hourly]） */
    @SerializedName("total") val total: Int = 0,
    /** 按**用户填的告警文本**分组；缺文本的行后端归到 `UNKNOWN` 桶 */
    @SerializedName("byText") val byText: List<StatisticsCount> = emptyList(),
    @SerializedName("hourly") val hourly: List<StatisticsBucket> = emptyList()
)

/**
 * 故障：窗口内的总数与按整点的时间线。
 *
 * 注意口径：故障行在**写入时就按 message 去重**（同一条消息只记首次出现的那一行），
 * 所以这儿的数字是「有几种失败」而不是「失败了多少次」。
 *
 * 首页目前不用它（卡片已去掉），但接口照旧下发、这里照旧解出来 —— 这类整屏一次取回的接口，
 * 增减一张卡片不该牵动后端。
 */
data class FailureOverview(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("hourly") val hourly: List<StatisticsBucket> = emptyList()
)

/** 看板一屏的全部数字（一次请求） */
data class OverviewStatistics(
    /** 实际生效的区间起点（毫秒）；必填，回显用于确认窗口 */
    @SerializedName("from") val from: Long = 0,
    /** 实际生效的区间终点（毫秒）；不传时后端补的是「现在」，必须回显 —— 否则前端不知道曲线右端是几点 */
    @SerializedName("to") val to: Long = 0,
    @SerializedName("devices") val devices: DeviceOverview = DeviceOverview(),
    @SerializedName("services") val services: ServiceOverview = ServiceOverview(),
    @SerializedName("alarms") val alarms: AlarmOverview = AlarmOverview(),
    @SerializedName("failures") val failures: FailureOverview = FailureOverview()
)
