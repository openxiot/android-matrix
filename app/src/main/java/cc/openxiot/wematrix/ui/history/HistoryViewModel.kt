package cc.openxiot.wematrix.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.wematrix.data.api.DeviceEntity
import cc.openxiot.wematrix.data.api.ModbusHistoryCurrent
import cc.openxiot.wematrix.data.api.ModbusHistoryFailure
import cc.openxiot.wematrix.data.api.ModbusHistoryFailures
import cc.openxiot.wematrix.data.api.ModbusServiceBrief
import cc.openxiot.wematrix.data.repository.ModbusHistoryRepository
import cc.openxiot.wematrix.data.repository.SpaceRepository
import cc.openxiot.wematrix.ui.modbus.RangePreset
import cc.openxiot.wematrix.ui.modbus.presetWindow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 空间级失败清单一索取回的条数（后端夹到 [1, 1000]）。空间级查询的 limit 是**整份清单**的口径，
 * 不是每个服务各 200 —— 故障是去重过的（同一条错误持续存在只落一条），一小时的量远到不了这里。
 */
private const val FAILURE_LIMIT = 1000

/** 概览表的一行：一个服务 + 它的当前值快照（窗口内的失败另有一份清单，按 serviceId 归位） */
data class ServiceOverview(
    val service: ModbusServiceBrief,
    /** 当前值快照（与时间范围无关，只在进页面 / 刷新时取）；取不到时为 null */
    val current: ModbusHistoryCurrent?,
    /** `/current` 取数失败的原因；空串 = 取到了 */
    val currentError: String
)

/** 项目级失败清单的一行：一条失败属于哪个服务 */
data class FailureRow(
    /** 列表 key 用：一条失败由「服务 + 方法 + 首次出现时刻」唯一确定（同名同时刻的两个服务靠它分开） */
    val key: String,
    val serviceId: String,
    /** 空间图里对应的服务；查不到（服务刚被挪出空间/删掉）时为 null，那时只显示 id */
    val service: ModbusServiceBrief?,
    val item: ModbusHistoryFailure
)

/** 按类型汇总的一项（页面上只用到这两个数，故不再包一层 data class） */
data class FailureTypeCount(val type: String, val count: Int)

/**
 * 项目级历史页的状态。
 *
 * 两件事分得很开，故在 state 里也分开：**当前值快照**（在采方法数 / 最新采集时间，与时间范围无关）
 * 与**窗口内的失败**（跟着时间范围走）。切一档时间范围只重取后者 —— 快照跟窗口没关系，
 * 犯不着为切一档把 N 个 `/current` 再拉一遍（见 [reloadFailures]）。
 */
data class HistoryUiState(
    /** 空间图加载中（服务清单是整页的骨架） */
    val isLoadingGraph: Boolean = true,
    /** 取数中 */
    val isLoading: Boolean = false,
    val services: List<ModbusServiceBrief> = emptyList(),
    /** did → 设备：概览卡里显示依赖设备的在线态（空间图里的设备，含没挂服务的那些） */
    val devices: Map<String, DeviceEntity> = emptyMap(),
    /** 空间图取数失败的原因：它取不到就没什么可看的，整页报错 */
    val graphError: String? = null,
    /** 逐服务的当前值快照 */
    val overview: List<ServiceOverview> = emptyList(),
    /** 空间级失败清单（一次取回整个项目）；取不到时为 null */
    val failures: ModbusHistoryFailures? = null,
    /** `/failures` 取数失败的原因；空串 = 取到了 */
    val failuresError: String = "",

    // —— 查询条件 ——
    /** 默认最近 24 小时：项目级看「昨天到今天」的多，实时看 1 小时就够，翻旧账才切 7 天 */
    val preset: RangePreset = RangePreset.HOUR_24,
    val customFrom: Long? = null,
    val customTo: Long? = null
) {
    /**
     * 生效的窗口（毫秒）：预设档 = 当下往前推，自定义档 = 用户选的两天。
     *
     * 自定义还没选全时返回 null —— 那时候没有合法窗口可算，页面据此不出网。
     */
    val window: Pair<Long, Long>?
        get() = presetWindow(preset, System.currentTimeMillis())
            ?: customFrom?.let { from -> customTo?.let { to -> from to to } }

    /** 某几格取不到数的原因（「服务名: 消息」），去重后列在页面顶部 */
    val loadErrors: List<String>
        get() = (overview.map { it.currentError } + failuresError)
            .filter { it.isNotEmpty() }
            .distinct()

    /**
     * 每个服务在窗口内最近的一次失败（清单倒序，故各服务的第一条即最新）。
     *
     * 这个索引是**派生**的而不是取数时算好的：换时间范围只重取失败清单，概览的行序要跟着它变。
     * 用 LinkedHashMap 保序，`putIfAbsent` 让第一条（最新）留下。
     */
    val latestFailureByService: Map<String, ModbusHistoryFailure>
        get() {
            val latest = LinkedHashMap<String, ModbusHistoryFailure>()
            failures?.items?.forEach { item ->
                latest.putIfAbsent(item.serviceId ?: "", item)
            }
            return latest
        }

    /**
     * 概览卡的行序：窗口内有异常的服务排前面（同有异常的按最近一次失败时刻倒序），其余保持空间图顺序。
     * 这个页面的常客是「项目里现在哪儿不对」，不是「服务清单」。`sortedWith` 是稳定排序，
     * 故没异常的那部分（以及失败时刻相同的那些）顺序不变。
     */
    val overviewRows: List<ServiceOverview>
        get() {
            val latest = latestFailureByService
            return overview.sortedWith { a, b ->
                val left = latest[a.service.id]?.at
                val right = latest[b.service.id]?.at
                when {
                    left == null && right == null -> 0
                    left == null -> 1
                    right == null -> -1
                    else -> right.compareTo(left)
                }
            }
        }

    /** 失败清单逐条挂回空间图里的服务（后端已排好序、截断好，这里不再自己排一遍） */
    val failureRows: List<FailureRow>
        get() {
            val byId = services.associateBy { it.id }
            return failures?.items.orEmpty().mapIndexed { index, item ->
                val serviceId = item.serviceId ?: ""
                FailureRow(
                    // 末尾那个序号是**去重用的**，不是展示用的：后端按「消息」去重，
                    // 同一个方法在同一毫秒上记两条不同消息是可能的，只拿前三个字段做键会撞 ——
                    // 而 LazyColumn 的键撞了是直接抛异常，整页白掉
                    key = "$serviceId#${item.functionIndex}#${item.at}#$index",
                    serviceId = serviceId,
                    service = byId[serviceId],
                    item = item
                )
            }
        }

    /**
     * 按类型汇总（条数降序、条数相同按键升序 —— 与后端 summary 的排序口径一致）。
     * 没带 type 的老数据不进汇总（它们在下表里显示为 `-`），免得堆出一个含义不明的标签。
     */
    val failureSummary: List<FailureTypeCount>
        get() {
            val counts = LinkedHashMap<String, Int>()
            failures?.items?.forEach { item ->
                val type = item.type
                if (!type.isNullOrEmpty()) counts[type] = (counts[type] ?: 0) + 1
            }
            return counts.map { FailureTypeCount(it.key, it.value) }
                .sortedWith(compareByDescending<FailureTypeCount> { it.count }.thenBy { it.type })
        }

    /** 窗口内的失败多于 limit：只列了最近的那部分 */
    val truncated: Boolean get() = failures?.truncated == true

    /**
     * 项目里最近一次成功采集的时刻：停在这儿说明整个项目都不采了，比任何一行的异常都严重。
     * 一个都没采到就是 null（页面显示 `-`），不是 0。
     */
    val lastRecordedAt: Long?
        get() = overview.mapNotNull { recordedAt(it.current) }.maxOrNull()

    val deviceCount: Int get() = devices.size

    /** 在采方法数：`/current` 只列**有采集状态**的方法（没配轮询或从没采过的不在其中） */
    fun sampledCount(row: ServiceOverview): String? = row.current?.functions?.size?.toString()

    /** 该服务最后一次成功采集的时刻（各方法里取最近的） */
    fun recordedAt(row: ServiceOverview): Long? = recordedAt(row.current)

    /** 该服务在窗口内最近的一次失败（清单倒序，故各服务的第一条即最新） */
    fun recentFailure(serviceId: String?): ModbusHistoryFailure? = latestFailureByService[serviceId]

    /**
     * 快照里最后一次成功采集的时刻（各方法里取最近的）；一个方法都没采到就是 null。
     *
     * 这里也顺带承担「该方法有没有采到过」的判断：一次都没成功过的方法**没有** `recordedAt`，
     * 故不能用「方法数」冒充「在采方法数」。
     */
    private fun recordedAt(current: ModbusHistoryCurrent?): Long? =
        current?.functions.orEmpty().mapNotNull { it.recordedAt }.maxOrNull()
}

/**
 * 项目级历史（当前项目下所有服务的采集历史），对齐 webapp-matrix 的 `pages/main/history/`。
 *
 * web 侧这里有「所有服务」二级页的入口（一张把每个服务每个字段都摊开的宽表）；**本端不做那一级**
 * ——手机屏上不可用，且这一页已经承担了「哪个服务不对」的判断，再往下点进单服务历史页看曲线
 * （见 `ServiceHistoryScreen`）。
 *
 * 骨架是空间图（服务清单与设备都在里面，取不到就没什么可看的）；采集数据分两块取：
 * **当前值快照**逐服务扇出（后端只有按服务的 `/history/current`），**窗口内的失败**用一条空间级
 * `/history/failures`（不传 serviceId）就够 —— 后端把空间下所有服务的失败合并、排序、截断，
 * 前端只按 item 上的 serviceId 归位，不必拿 N 个响应在内存里拼。
 *
 * `rootId` 是当前项目**根空间**：既是鉴权作用域，也是查询范围（含子空间）。
 */
class HistoryViewModel : ViewModel() {
    private val historyRepository = ModbusHistoryRepository()
    private val spaceRepository = SpaceRepository()

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var rootId: String? = null

    /**
     * 进页面：先取空间图（服务清单与设备是整页的骨架），到手后再取数。
     *
     * 与告警页不同，空间图在这页**取不到就真没什么可看的**：没有服务清单就无从扇出 `/current`，
     * 故它是整页的错误态，而不是「退回 id 照常用」。
     */
    fun load(rootId: String) {
        this.rootId = rootId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingGraph = true, graphError = null)
            spaceRepository.getSpaceGraph(rootId)
                .onSuccess { graph ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingGraph = false,
                        services = graph.services.orEmpty(),
                        // 空间图里的设备含没挂服务的那些，故整份收下（只用到 did 与 online）
                        devices = graph.devices.orEmpty()
                            .mapNotNull { device -> device.did?.let { it to device } }
                            .toMap()
                    )
                    // 服务清单到手才谈得上逐服务取数
                    refresh()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingGraph = false,
                        graphError = e.message ?: "获取空间图失败"
                    )
                }
        }
    }

    /**
     * 按当前条件重算窗口，快照与失败清单**一并重取**（进页面与「刷新」走这里）。
     * 预设是「最近 N」，故每次都重新对齐到现在 —— 页面开着不动窗口不会自己走，刷新即跟上。
     */
    fun refresh() {
        val spaceId = rootId ?: return
        val window = _uiState.value.window ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // 快照逐服务扇出：并发发，N 个服务的耗时是一个的量级。
            // 单个服务失败只让那一行显示 `-`（原因记在 currentError 里），不拖垮整页
            val services = _uiState.value.services
            val overview = coroutineScope {
                services.map { service ->
                    async {
                        val id = service.id.orEmpty()
                        historyRepository.current(spaceId, id).fold(
                            onSuccess = { ServiceOverview(service, it, "") },
                            onFailure = { e ->
                                ServiceOverview(service, null, "${service.name ?: id}: ${e.message ?: "获取采集状态失败"}")
                            }
                        )
                    }
                }.awaitAll()
            }

            val failures = historyRepository.failures(
                spaceId = spaceId,
                from = window.first,
                to = window.second,
                limit = FAILURE_LIMIT
            )

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                overview = overview,
                failures = failures.getOrNull(),
                failuresError = failures.exceptionOrNull()?.message.orEmpty()
            )
        }
    }

    /**
     * 只重取失败清单（换时间范围走这里）：当前值快照与窗口无关，不必跟着重取。
     *
     * 快照还没取到时直接返回 —— 那时候扇出还没发生（或已整页失败），
     * 补一次只取失败清单的请求会得到一个没有服务清单可归位的清单。
     */
    fun reloadFailures() {
        val spaceId = rootId ?: return
        if (_uiState.value.overview.isEmpty()) return
        val window = _uiState.value.window ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = historyRepository.failures(
                spaceId = spaceId,
                from = window.first,
                to = window.second,
                limit = FAILURE_LIMIT
            )
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                failures = result.getOrNull(),
                failuresError = result.exceptionOrNull()?.message.orEmpty()
            )
        }
    }

    /** 切预设档。选到「自定义」时只换档、不取数 —— 窗口还没定，等用户选完日期 */
    fun setPreset(preset: RangePreset) {
        if (preset == _uiState.value.preset) return
        _uiState.value = _uiState.value.copy(preset = preset)
        if (preset.isPreset) reloadFailures()
    }

    /** 自定义区间选完（两个绝对时刻，已按整天展开） */
    fun setCustomRange(from: Long, to: Long) {
        _uiState.value = _uiState.value.copy(
            preset = RangePreset.CUSTOM,
            customFrom = from,
            customTo = to
        )
        reloadFailures()
    }
}
