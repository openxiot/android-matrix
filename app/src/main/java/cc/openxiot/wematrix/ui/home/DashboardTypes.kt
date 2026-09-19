package cc.openxiot.wematrix.ui.home

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

    // ---- 默认尺寸：HALF 仅 stat 卡 ----
    fun defaultSize(type: String?): String = if (type == STAT) SIZE_HALF else SIZE_FULL

    /** 是否允许某尺寸档位（HALF 仅 stat）。 */
    fun sizeAllowed(type: String?, size: String?): Boolean =
        size == SIZE_FULL || (size == SIZE_HALF && type == STAT)

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

    /** 无标题/无 titleKey 时的类型默认名（中文，对齐 web 词典）。 */
    fun defaultTitle(type: String?): String = when (type) {
        STAT -> "统计数字"
        LINE -> "曲线图"
        DISTRIBUTION -> "数据分布"
        DEVICE -> "设备"
        SERVICE -> "服务"
        else -> "卡片"
    }

    /**
     * 标题解析：`title`（用户数据，原样显示）→ `titleKey` → 类型默认名。
     *
     * 服务端预置（MobileDashboardPresetLayouts）的 titleKey **就是中文展示文本本身**
     * （如「设备总量」）—— 与 web 那套「key 即原文、按语言表翻译」是同构的，只是本端单语言、
     * 无处翻译，故直接原样返回。
     */
    fun resolveTitle(widgetTitle: String?, titleKey: String?, type: String?): String {
        val user = widgetTitle?.takeIf { it.isNotBlank() }
        val preset = titleKey?.takeIf { it.isNotBlank() }
        return user ?: preset ?: defaultTitle(type)
    }
}