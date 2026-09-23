package cc.openxiot.wematrix.ui.history

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.wematrix.R
import cc.openxiot.wematrix.data.api.DeviceEntity
import cc.openxiot.wematrix.data.api.ModbusHistoryCurrent
import cc.openxiot.wematrix.data.api.ModbusHistoryFailures
import cc.openxiot.wematrix.data.api.ModbusHistoryRange
import cc.openxiot.wematrix.data.api.ModbusService
import cc.openxiot.wematrix.data.api.ModbusServiceField
import cc.openxiot.wematrix.data.api.ModbusServiceFunction
import cc.openxiot.wematrix.data.repository.DeviceRepository
import cc.openxiot.wematrix.data.repository.ModbusHistoryRepository
import cc.openxiot.wematrix.data.repository.ModbusServiceRepository
import cc.openxiot.wematrix.ui.core.UiText
import cc.openxiot.wematrix.ui.core.toUiText
import cc.openxiot.wematrix.ui.modbus.RangePreset
import cc.openxiot.wematrix.ui.modbus.historyPointText
import cc.openxiot.wematrix.ui.modbus.historyTimeText
import cc.openxiot.wematrix.ui.modbus.isBucket
import cc.openxiot.wematrix.ui.modbus.isReadFunction
import cc.openxiot.wematrix.ui.modbus.presetWindow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 单次取数的最大点数：表格要尽量多的原始点（一行一条采样，点越多越接近「每一次采集」），
 * 曲线图按后端缺省值就够（再多也画不出来，只会把线画糊）。
 * 两个值都在后端 [1, 2000] 的夹取范围内，超过它才返回降采样桶。
 */
private const val TABLE_MAX_POINTS = 2000
private const val CHART_MAX_POINTS = 500

/** 失败清单一次取回的条数（后端夹到 [1, 1000]）；超出时后端会给 truncated 标志 */
private const val FAILURE_LIMIT = 200

/**
 * 曲线图默认勾选的字段数（方法多、字段多的服务一进页面就画几十张图既慢又看不过来），
 * 只是**默认**选择：字段选择里能改，且不做数量上限 —— 勾了几张就画几张。
 */
private const val DEFAULT_CHART_FIELDS = 12

/**
 * 展示形式：一行一条采样的长表 / 每个字段一张小图。
 *
 * 标签给**资源 id**（规则 A：1:1 的固定标签），语言由调用方 `stringResource` 注入 ——
 * 枚举是纯 Kotlin，没有 Context 也不该有。
 */
enum class HistoryView(@StringRes val labelRes: Int) {
    TABLE(R.string.service_history_view_table),
    CHART(R.string.service_history_view_chart)
}

/**
 * 一个能取数的字段（应答字段本身，或位区展开出来的某一位）。
 *
 * `functionIndex` / `field` / `unit` 都是**服务端数据，原样显示、永不翻译**。
 */
data class FieldRef(
    /** 同一个字段名可以出现在不同方法里，故缓存键带上方法序号 */
    val key: String,
    val functionIndex: Int,
    val functionName: String,
    val field: String,
    val unit: String,
    /** 开关量（位区逐位展开出来的 0/1）：曲线走阶梯，不走直连 */
    val step: Boolean,
    /** 数值才画得出来（取值表命中的描述串画不了曲线，但表格照样列） */
    val numeric: Boolean
)

/** 一个字段的取数结果：失败时只留 ref 与 error，其余字段不参与展示 */
data class FieldData(
    val ref: FieldRef,
    val range: ModbusHistoryRange? = null,
    val error: UiText? = null
)

/** 表格里的一行：原始样本一行一条，降采样桶也占一行（一行 = 一段） */
data class HistoryRow(
    val key: String,
    val at: Long,
    val time: String,
    val functionIndex: Int,
    val functionName: String,
    val field: String,
    val value: String,
    val unit: String,
    /** 值没变、按 keep-alive 时限补记的一条的角标；普通样本为 null */
    val note: UiText?
)

/** 一张曲线图的渲染输入 */
data class HistoryChart(
    val ref: FieldRef,
    /** 取数失败的原因；非空时这张图位置显示错误，不画图 */
    val error: UiText?,
    /** 取到的序列；没取到（或失败）时为 null */
    val range: ModbusHistoryRange?,
    /** 展示用：`field (unit)` —— 单位是数据、原样缀上 */
    val title: String
)

/**
 * 单服务历史页的状态。
 *
 * 窗口与「方法 / 显示方式 / 字段」三组条件放在同一个 state 里：这一页**换任何一项都要重取**
 * （与项目级历史页不同 —— 那边切时间范围只重取失败清单），故不必拆开。
 */
data class ServiceHistoryUiState(
    /** 服务定义加载中（整页的骨架：字段清单就来自它） */
    val isLoadingService: Boolean = true,
    /** 取数中 */
    val isLoading: Boolean = false,
    val service: ModbusService? = null,
    /** 服务定义取不到：整页报错 */
    val serviceError: UiText? = null,
    /** 依赖设备（只用于展示在线态），取不到不影响历史数据本身 */
    val device: DeviceEntity? = null,
    /** 各方法最后一次成功采到的值（只喂页头两格） */
    val current: ModbusHistoryCurrent? = null,

    val data: List<FieldData> = emptyList(),
    val failures: ModbusHistoryFailures? = null,
    val failuresError: UiText? = null,

    // —— 查询条件 ——
    /** 默认最近 1 小时：这一页看的是「现在怎么样」，翻旧账才切档 */
    val preset: RangePreset = RangePreset.HOUR_1,
    val customFrom: Long? = null,
    val customTo: Long? = null,
    /** 方法筛选：0 = 全部方法（方法序号是 1 起自然数，0 空着，省得下拉的 null 值来回折腾） */
    val functionIndex: Int = 0,
    val view: HistoryView = HistoryView.TABLE,
    /** 曲线图勾选的字段（[FieldRef.key]）；换方法时按新范围的数值字段重置 */
    val chartFields: List<String> = emptyList()
) {
    /**
     * 生效的窗口（毫秒）：预设档 = 当下往前推，自定义档 = 用户选的两天。
     * 自定义还没选全时返回 null —— 那时候没有合法窗口可算，页面据此不出网。
     */
    val window: Pair<Long, Long>?
        get() = presetWindow(preset, System.currentTimeMillis())
            ?: customFrom?.let { from -> customTo?.let { to -> from to to } }

    val functions: List<ModbusServiceFunction> get() = service?.functions.orEmpty()

    /** 能配轮询的只有读方法，历史也只可能出在它们身上，故方法筛选只列这些 */
    val readFunctions: List<ModbusServiceFunction> get() = functions.filter { isReadFunction(it) }

    /** 当前方法筛选下的全部字段（表格列的就是这些；曲线图只用其中数值的那部分） */
    val fields: List<FieldRef>
        get() {
            val pick = functionIndex
            val refs = mutableListOf<FieldRef>()
            for (func in readFunctions) {
                if (pick > 0 && func.index != pick) continue
                for (field in func.response?.fields.orEmpty()) {
                    refs += fieldRef(func, field, step = false)
                    for (bit in field.bitList) {
                        val name = bit.field ?: continue
                        // 位区展开出来的位是同一次调用的另外几个取值，各自也有一条历史
                        refs += FieldRef(
                            key = refKey(func.index ?: 0, name),
                            functionIndex = func.index ?: 0,
                            functionName = func.name.orEmpty(),
                            field = name,
                            unit = "",
                            step = true,
                            numeric = true
                        )
                    }
                }
            }
            return refs
        }

    /** 画得出曲线的字段：取值表命中的字符串字段只能进表格 */
    val numericFields: List<FieldRef> get() = fields.filter { it.numeric }

    /** 曲线图要画的字段：勾选 ∩ 可得字段（换方法后勾选里可能留着别的方法的键，故要夹一次） */
    val chartRefs: List<FieldRef>
        get() {
            val selected = chartFields.toSet()
            return numericFields.filter { selected.contains(it.key) }
        }

    /**
     * 表格行：按时间倒序（最新的在最上面），桶行的时间写成「桶起点 ~ 桶终点」。
     *
     * 采集是「值变了才记一条」，故同一时刻的那几行**保持方法/字段的定义顺序**（排序是稳定的），
     * 看着才有规律。
     */
    val rows: List<HistoryRow>
        get() {
            val rows = mutableListOf<HistoryRow>()
            data.forEach { item ->
                item.range?.points?.forEachIndexed { position, point ->
                    rows += HistoryRow(
                        // 末尾那个序号是**去重用的**：同一个字段在同一毫秒上理论上可以有两条
                        // （值变一条 + 保持一条），只拿时刻与字段做键会撞 ——
                        // 而 LazyColumn 的键撞了是直接抛异常，整页白掉
                        key = "${point.at}#${item.ref.key}#$position",
                        at = point.at,
                        // 桶写成「起点 ~ 终点」（跨天时终点写全），样本只写时刻，见 historyTimeText
                        time = historyTimeText(point.at, point.until),
                        functionIndex = item.ref.functionIndex,
                        functionName = item.ref.functionName,
                        field = item.ref.field,
                        value = historyPointText(point),
                        unit = item.ref.unit,
                        // 值没变、按 keep-alive 时限补记的一条：与「变了才记」区分开
                        note = if (!isBucket(point) && point.keepalive == true) {
                            UiText.Res(R.string.service_history_keepalive)
                        } else {
                            null
                        }
                    )
                }
            }
            return rows.sortedByDescending { it.at }
        }

    /** 方法序号 → 该方法在窗口内的失败时刻（图上画竖线用） */
    val failureTimes: Map<Int, List<Long>>
        get() {
            val map = mutableMapOf<Int, MutableList<Long>>()
            failures?.items?.forEach { item ->
                map.getOrPut(item.functionIndex) { mutableListOf() }.add(item.at)
            }
            return map
        }

    /** 逐字段的图（顺序 = 勾选顺序夹在字段定义顺序上，见 [chartRefs]） */
    val charts: List<HistoryChart>
        get() {
            val byKey = data.associateBy { it.ref.key }
            return chartRefs.map { ref ->
                val item = byKey[ref.key]
                HistoryChart(
                    ref = ref,
                    error = item?.error,
                    range = item?.range,
                    title = if (ref.unit.isNotEmpty()) "${ref.field} (${ref.unit})" else ref.field
                )
            }
        }

    /** 命中降采样时给一句说明：表格里的括号与图上的虚线都靠它解释 */
    val downsampled: Boolean get() = data.any { it.range?.downsampled == true }

    /** 取数失败的字段：一个字段失败不影响其余，表格没有挂错处，去重后统一提示在内容上方 */
    val loadErrors: List<UiText>
        get() = data.mapNotNull { it.error }.distinct()

    /** 页头：最后一次成功采集的时刻（各方法里取最近的那个） */
    val lastRecordedAt: Long?
        get() = current?.functions.orEmpty().mapNotNull { it.recordedAt }.maxOrNull()

    /** 页头：真正采到过数据的方法数（配了轮询、且至少成功采过一次） */
    val sampledCount: Int
        get() = current?.functions.orEmpty().count { it.recordedAt != null }

    /** 页头「方法数」那一格：`已采到 / 可配轮询` —— 差得多说明有方法一直没采上 */
    val methodCountText: String
        get() = if (service == null) "-" else "$sampledCount / ${readFunctions.size}"

    /** 方法下拉里的一项：`#序号 名称` */
    fun functionLabel(func: ModbusServiceFunction): String =
        "#${func.index ?: 0} ${func.name.orEmpty()}".trim()
}

/**
 * 单服务历史页：一个服务的逐字段采集历史，对齐 webapp-matrix 的
 * `pages/main/device/services/service/history/`。
 *
 * **一个字段一个请求**（`/history/range` 的口径就是单字段），逐个兜错：某个字段取不到
 * （窗口过密、后端拒绝）不该拖垮整页 —— 失败原因挂在它自己身上，表格那几行不出现。
 *
 * 同一份窗口数据有两种看法，**两种看法取的字段集与 maxPoints 都不同**（表格要全部字段、2000 点；
 * 曲线图只要画得出来的、500 点），故换一种看重要重取一次 —— 与项目级历史页那个「两种形式共用
 * 同一批数据」的取舍正好相反。
 */
class ServiceHistoryViewModel : ViewModel() {
    private val historyRepository = ModbusHistoryRepository()
    private val serviceRepository = ModbusServiceRepository()
    private val deviceRepository = DeviceRepository()

    private val _uiState = MutableStateFlow(ServiceHistoryUiState())
    val uiState: StateFlow<ServiceHistoryUiState> = _uiState.asStateFlow()

    private var rootId: String? = null
    private var serviceId: String? = null

    /**
     * 进页面：先取服务定义（字段清单就来自它，是整页的骨架），拿到后再取设备与数据。
     *
     * 设备（在线态）与当前值快照都**只用于展示摘要**，取不到就不显示 —— 它们不该挡住历史数据。
     */
    fun load(rootId: String, serviceId: String) {
        this.rootId = rootId
        this.serviceId = serviceId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingService = true, serviceError = null)
            serviceRepository.getService(rootId, serviceId)
                .onSuccess { service ->
                    _uiState.value = _uiState.value.copy(isLoadingService = false, service = service)
                    loadDevice(rootId, service.device?.did)
                    resetChartFields()
                    // 字段要等定义到手才解析得出来，取数从这里起步
                    load()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingService = false,
                        serviceError = e.toUiText(R.string.err_modbus_service_get)
                    )
                }
        }
    }

    /** 设备只用于展示摘要（在线态），取不到不影响历史数据本身 */
    private fun loadDevice(rootId: String, did: String?) {
        if (did.isNullOrEmpty()) return
        viewModelScope.launch {
            // 后端没有「按 did 取一个设备」的 GET，只能取整个空间的设备再挑 ——
            // 设备清单本来就整份取（设备页也是这么取的），多这一个请求不心疼；
            // 但只在 did 落在本空间时挑得出来，挑不到就不显示在线态
            val device = deviceRepository.getDevices(rootId).getOrNull()
                ?.firstOrNull { it.did == did }
            _uiState.value = _uiState.value.copy(device = device)
        }
    }

    /**
     * 按当前条件重算窗口并取数（「刷新」与切时间范围都走这里）。
     * 预设是「最近 N」，故每次都重新对齐到现在 —— 页面开着不动时窗口不会自己走，刷新即跟上。
     */
    fun load() {
        val window = _uiState.value.window ?: return
        reload(window)
    }

    private fun reload(window: Pair<Long, Long>) {
        val spaceId = rootId ?: return
        val service = serviceId ?: return
        val state = _uiState.value
        // 表格要看到全部字段（含只能显示、画不出来的字符串字段），曲线图只要画得出来的
        val chart = state.view == HistoryView.CHART
        val refs = if (chart) state.chartRefs else state.fields
        val maxPoints = if (chart) CHART_MAX_POINTS else TABLE_MAX_POINTS

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            loadCurrent(spaceId, service)
            loadFailures(spaceId, service, window)

            if (refs.isEmpty()) {
                // 一个字段都没有（服务还没展开出应答字段）：取数到此为止，别发一堆空请求
                _uiState.value = _uiState.value.copy(isLoading = false, data = emptyList())
                return@launch
            }

            val data = coroutineScope {
                refs.map { ref ->
                    async {
                        historyRepository.range(
                            spaceId = spaceId,
                            serviceId = service,
                            functionIndex = ref.functionIndex,
                            field = ref.field,
                            from = window.first,
                            to = window.second,
                            maxPoints = maxPoints
                        ).fold(
                            onSuccess = { FieldData(ref, it) },
                            onFailure = { e -> FieldData(ref, error = e.toUiText(R.string.err_sampling_data)) }
                        )
                    }
                }.awaitAll()
            }
            _uiState.value = _uiState.value.copy(isLoading = false, data = data)
        }
    }

    /** 当前值快照（只喂页头那两格：采到的方法数、最后采集时刻），取不到就不显示 */
    private suspend fun loadCurrent(spaceId: String, serviceId: String) {
        val current = historyRepository.current(spaceId, serviceId).getOrNull()
        _uiState.value = _uiState.value.copy(current = current)
    }

    private suspend fun loadFailures(spaceId: String, serviceId: String, window: Pair<Long, Long>) {
        val result = historyRepository.failures(
            spaceId = spaceId,
            from = window.first,
            to = window.second,
            serviceId = serviceId,
            // 方法筛选生效时只取该方法的失败：图上只画得出筛选后那些方法的竖线，
            // 取全部方法的会画出一些当前根本没在显示的线
            functionIndex = _uiState.value.functionIndex.takeIf { it > 0 },
            limit = FAILURE_LIMIT
        )
        _uiState.value = _uiState.value.copy(
            failures = result.getOrNull(),
            failuresError = result.exceptionOrNull()?.toUiText()
        )
    }

    /* 三组条件各一个入口，都是「改了就重取」 */

    /** 切预设档。选到「自定义」时只换档、不取数 —— 窗口还没定，等用户选完日期 */
    fun setPreset(preset: RangePreset) {
        if (preset == _uiState.value.preset) return
        _uiState.value = _uiState.value.copy(preset = preset)
        if (preset.isPreset) load()
    }

    /** 自定义区间选完（两个绝对时刻，已按整天展开） */
    fun setCustomRange(from: Long, to: Long) {
        _uiState.value = _uiState.value.copy(
            preset = RangePreset.CUSTOM,
            customFrom = from,
            customTo = to
        )
        load()
    }

    fun setFunction(functionIndex: Int) {
        if (functionIndex == _uiState.value.functionIndex) return
        _uiState.value = _uiState.value.copy(functionIndex = functionIndex)
        resetChartFields()
        afterConditionChange()
    }

    fun setView(view: HistoryView) {
        if (view == _uiState.value.view) return
        _uiState.value = _uiState.value.copy(view = view)
        afterConditionChange()
    }

    fun setChartFields(keys: List<String>) {
        _uiState.value = _uiState.value.copy(chartFields = keys)
        afterConditionChange()
    }

    /** 改条件后重取：窗口跟着重算（预设是「最近 N」，条件改完常常已经过了一小会儿） */
    private fun afterConditionChange() {
        val window = _uiState.value.window ?: return
        reload(window)
    }

    /** 默认勾选排在前面的若干个数值字段（不勾满：字段多时先给几张图看个大概） */
    private fun resetChartFields() {
        _uiState.value = _uiState.value.copy(
            chartFields = _uiState.value.numericFields.take(DEFAULT_CHART_FIELDS).map { it.key }
        )
    }
}

/** 缓存键：字段名在方法之间可能重名，故带上方法序号 */
private fun refKey(functionIndex: Int, field: String): String = "$functionIndex#$field"

/** 应答字段 → 可取数的字段；数值判定见 [FieldRef.numeric] 的说明 */
private fun fieldRef(func: ModbusServiceFunction, field: ModbusServiceField, step: Boolean): FieldRef {
    val name = field.field.orEmpty()
    return FieldRef(
        key = refKey(func.index ?: 0, name),
        functionIndex = func.index ?: 0,
        functionName = func.name.orEmpty(),
        field = name,
        unit = field.unit.orEmpty(),
        step = step,
        // 取值表命中时字段值直接是描述串（不再缩放），画不成曲线；string 同理
        numeric = field.format != "string" && field.valueList.isEmpty()
    )
}
