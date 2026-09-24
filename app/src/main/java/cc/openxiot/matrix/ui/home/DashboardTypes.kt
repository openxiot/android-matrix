package cc.openxiot.matrix.ui.home
import androidx.annotation.StringRes
import cc.openxiot.matrix.R

/**
 * 移动端看板的类型常量与默认值，对齐后端 `DashboardWidgetStructureValidator` 与
 * webapp-matrix 的 `widget.picker.ts` / `dashboard.charts.ts`。
 *
 * **允许值集合以这里为唯一真源**：编辑器下拉、取数后的解析、dirty 判定都读它。
 * 五类卡片的 config 键语义与 web 逐字同构（共享渲染核心），故这里只列「枚举值」，
 * 不另造 config 结构。
 */
object DashboardTypes {

    // ---- 类型 ----
    const val STAT = "stat"
    const val LINE = "line"
    const val DISTRIBUTION = "distribution"
    const val DEVICE = "device"
    const val SERVICE = "service"

    val ALL = listOf(STAT, LINE, DISTRIBUTION, DEVICE, SERVICE)

    // ---- 尺寸档位 ----
    const val SIZE_FULL = "FULL"
    const val SIZE_HALF = "HALF"

    // ---- 半宽卡占哪半格（只有 HALF 卡有） ----
    /** 占左半格：另起一行（右边没别的卡就是空的）。 */
    const val SIDE_LEFT = "LEFT"
    /** 占右半格：当前行右半格空着就填进去，否则另起一行、左半格空着。 */
    const val SIDE_RIGHT = "RIGHT"

    // ---- stat.metric ----
    val STAT_METRICS = listOf(
        "devices.total", "devices.online", "services.total",
        "alarms.today", "alarms.window", "failures.total"
    )

    // ---- distribution.dimension ----
    val DIMENSIONS = listOf("deviceType", "serviceType", "alarmType", "failureType")

    // ---- line.source ----
    val LINE_SOURCES = listOf("alarmCount", "serviceField")

    // ---- 需要 window 的判定 ----
    private val STAT_NEEDS_WINDOW = setOf("alarms.today", "alarms.window", "failures.total")
    private val DIMENSION_NEEDS_WINDOW = setOf("alarmType", "failureType")

    fun statNeedsWindow(metric: String?): Boolean = metric in STAT_NEEDS_WINDOW
    fun distributionNeedsWindow(dimension: String?): Boolean = dimension in DIMENSION_NEEDS_WINDOW
    fun lineNeedsWindow(source: String?): Boolean = true // 两种 source 都要 window

    /**
     * 半宽开放给哪些类型：**与后端逐字同一条口径**
     * （`MobileDashboardWidgetStructureValidator.HALF_TYPES`）—— 这两处必须一起改，
     * 客户端松了口而后端不认，用户点「确认」时会收到一条保存失败。
     *
     * 统计卡（两块小数字并列）与服务卡（一行「字段名 + 值」）半宽放得下；曲线/分布图半宽画不开。
     */
    private val HALF_TYPES = setOf(STAT, SERVICE)

    // ---- 默认尺寸：只有统计卡**默认**半宽（服务卡仍是整宽，半宽是可选档） ----
    fun defaultSize(type: String?): String = if (type == STAT) SIZE_HALF else SIZE_FULL

    /** 是否允许某尺寸档位（HALF 仅 stat / service）。 */
    fun sizeAllowed(type: String?, size: String?): Boolean =
        size == SIZE_FULL || (size == SIZE_HALF && type in HALF_TYPES)

    /** picker 加卡时的默认 config，镜像 web `widget.picker.ts`。 */
    fun defaultConfig(type: String?): Map<String, Any?> = when (type) {
        STAT -> mapOf("metric" to "devices.total")
        LINE -> mapOf(
            "source" to "alarmCount",
            "bucket" to "hour",
            "window" to mapOf("kind" to "last", "hours" to 24)
        )
        DISTRIBUTION -> mapOf("dimension" to "deviceType")
        else -> emptyMap() // device / service：无可默认，进编辑器让用户从 catalog 选
    }

    /**
     * 无标题/无 titleKey 时的类型默认名（对齐 web 词典）。
     *
     * 返回**资源 id** 而不是 String：这个对象是纯 Kotlin，没有 Context 也不该有 ——
     * 它有一份逐条钉死行为的单测，塞个 Context 进去那些测试就跑不动了。
     * 语言由调用方 `stringResource(...)` 注入（规则 A：1:1 的固定标签返回 `@StringRes`）。
     */
    @StringRes
    fun defaultTitleRes(type: String?): Int = when (type) {
        STAT -> R.string.home_card_default_stat
        LINE -> R.string.home_card_default_line
        DISTRIBUTION -> R.string.home_card_default_distribution
        DEVICE -> R.string.home_card_default_device
        SERVICE -> R.string.home_card_default_service
        else -> R.string.home_card_default_unknown
    }

    /**
     * 标题解析：`title`（用户数据，原样显示）→ `titleKey` → [fallback]。
     *
     * 这里**只管优先级，不碰语言**：只有最后一档需要文案，而文案要 Context。拆开的两个理由 ——
     * 调用方本来就在 composable 里（[DashboardWidgetHost]），三合一的话它得先弄到一个 Context
     * 才知道自己要显示什么；而且「类型 → 默认名」这件事只有 [defaultTitleRes] 一个出口，
     * 两个函数各管一段，编辑器那两处也能直接用上第三档。
     *
     * 服务端预置（MobileDashboardPresetLayouts）的 titleKey **就是中文展示文本本身**
     * （如「设备总量」）—— 与 web 那套「key 即原文、按语言表翻译」是同构的，只是本端单语言、
     * 无处翻译，故直接原样返回。
     */
    fun resolveTitle(widgetTitle: String?, titleKey: String?, fallback: String): String {
        val user = widgetTitle?.takeIf { it.isNotBlank() }
        val preset = titleKey?.takeIf { it.isNotBlank() }
        return user ?: preset ?: fallback
    }

    // ---- config 改写 ----

    /**
     * config 改写：**`null` 是删键，不是写一个「键在、值为 null」的项**。
     *
     * 两条理由：
     * - 与 web 同口径 —— 那边「没配」就是键不存在（`setConfig(k, undefined)`，codec 直接不发）；
     * - 后端校验器对「键在、值为 null」是要**报错**的：`booleanErr` 按 `containsKey` 判
     *   （null 不是 Boolean → `config.showUnit must be a boolean`），`limit` 同理
     *   （`config.limit must be a number`）。
     *
     * 返回新 map，**不改**传进来的那份。
     */
    fun configWith(config: Map<String, Any?>, key: String, value: Any?): Map<String, Any?> =
        if (value == null) config - key else HashMap(config).apply { this[key] = value }

    /**
     * **一次**交互写多个键：`configWith(config, "serviceId" to id, "functionIndex" to 1, "fields" to null)`。
     *
     * 存在的理由是一个很难自己发现的坑：传给编辑器的 `config` 是**组合时那份 map**（普通参数，
     * 不是 State），所以连写两次 —— `onChange(configWith(config, "a", 1))` 之后再来一次
     * `onChange(configWith(config, "b", 2))` —— 两次都拿着同一份旧 map 算，后者把前者**整个覆盖**，
     * 净效果等于一个字都没改。换服务要同时改三个键，撞上的就是这个：服务框弹回「请选择服务」，
     * 看着像「选不中」。要走出口只有在**一次** onChange 里写完，这个函数就是那个「一次」。
     */
    fun configWith(config: Map<String, Any?>, vararg pairs: Pair<String, Any?>): Map<String, Any?> =
        pairs.fold(config) { acc, (key, value) -> configWith(acc, key, value) }

    /**
     * config 里的布尔开关：**缺键、或者值不是 Boolean 时取 [default]**。
     *
     * 与 web `readBoolean(config, key, default)` 逐条同口径（那边把 `'false'` 这种字符串、
     * `0`、`[]`、`null` 都钉了「走缺省」，见 `dashboard.config.spec.ts`）。三个开关
     * （`showUnit` / `showFailureShadow`）的缺省都是「显示 / 画」。
     */
    fun configBool(config: Map<String, Any?>, key: String, default: Boolean): Boolean =
        config[key] as? Boolean ?: default

    /** config 里的整数项（`limit` / `maxPoints`）：不是数字就是「没设」（null）。 */
    fun configInt(config: Map<String, Any?>, key: String): Int? = (config[key] as? Number)?.toInt()

    // ---- 时间窗口 ----

    /** 相对窗口的常见档（小时），配了就可直接存 `{kind:"last", hours:n}`。 */
    val WINDOW_HOURS = listOf(1, 6, 24, 72, 168)

    fun defaultWindow(): Map<String, Any?> = mapOf("kind" to "last", "hours" to 24)

    /** 这一栏该不该给 window 配置（按类型 + 当前选值）。 */
    fun needsWindow(type: String?, config: Map<String, Any?>): Boolean = when (type) {
        STAT -> statNeedsWindow(config["metric"] as? String)
        LINE -> true
        DISTRIBUTION -> distributionNeedsWindow(config["dimension"] as? String)
        else -> false
    }

    /**
     * window 合法性：`{kind:"last", hours:1..744}` 或 `{kind:"range", from<to}`。
     * 缺 window（本卡不需要）也算合法。
     */
    @Suppress("UNCHECKED_CAST")
    fun windowValid(type: String?, config: Map<String, Any?>): Boolean {
        if (!needsWindow(type, config)) return true
        val w = config["window"] as? Map<*, *> ?: return false
        return when (w["kind"]) {
            "last" -> (w["hours"] as? Number)?.toLong()?.let { it in 1..744 } == true
            "range" -> {
                val from = (w["from"] as? Number)?.toLong()
                val to = (w["to"] as? Number)?.toLong()
                from != null && to != null && from < to
            }
            else -> false
        }
    }

    /** 这张卡的 config 是否齐了（编辑器「保存」在齐之前禁用，镜像 web `canCommit`）。 */
    @Suppress("UNCHECKED_CAST")
    fun canCommit(type: String?, config: Map<String, Any?>): Boolean {
        // 各分支都收成一个 Boolean 表达式，别用 return 打断 when 的类型推导
        return when (type) {
            STAT -> !(config["metric"] as? String).isNullOrBlank() && windowValid(type, config)
            LINE -> {
                val source = config["source"] as? String
                when {
                    source.isNullOrBlank() || !windowValid(type, config) -> false
                    source == "alarmCount" -> true
                    else -> {
                        val fn = (config["functionIndex"] as? Number)?.toInt() ?: 0
                        !(config["field"] as? String).isNullOrBlank()
                            && (config["serviceId"] as? String)?.isNotBlank() == true
                            && fn >= 1
                    }
                }
            }
            DISTRIBUTION -> !(config["dimension"] as? String).isNullOrBlank() && windowValid(type, config)
            DEVICE -> !(config["did"] as? String).isNullOrBlank() && !(config["pid"] as? String).isNullOrBlank()
            SERVICE -> {
                val fields = config["fields"] as? List<*>
                val fn = (config["functionIndex"] as? Number)?.toInt() ?: 0
                !(config["serviceId"] as? String).isNullOrBlank() && fn >= 1 && !fields.isNullOrEmpty()
            }
            else -> false
        }
    }
}