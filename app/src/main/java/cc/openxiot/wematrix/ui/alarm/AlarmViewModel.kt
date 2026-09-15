package cc.openxiot.wematrix.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.wematrix.data.api.ModbusAlarm
import cc.openxiot.wematrix.data.api.ModbusAlarmList
import cc.openxiot.wematrix.data.api.ModbusAlarmQuery
import cc.openxiot.wematrix.data.api.ModbusServiceBrief
import cc.openxiot.wematrix.data.repository.ModbusAlarmRepository
import cc.openxiot.wematrix.data.repository.SpaceRepository
import cc.openxiot.wematrix.ui.modbus.RangePreset
import cc.openxiot.wematrix.ui.modbus.applyHandledAlarm
import cc.openxiot.wematrix.ui.modbus.presetWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 一次取回的条数上限（后端夹到 [1, 1000]，缺省 200）。与 web 的 `ALARM_LIMIT` 同值。
 *
 * 取到上限与「不限」不是一回事：超了后端给 `truncated = true`，页面据此提示缩小时间范围。
 */
private const val ALARM_LIMIT = 500

/**
 * 告警页的状态。
 *
 * 查询条件与取数结果放在同一个 state 里：这一页**只有一条请求**（`/alarm/many` 不传 serviceId
 * 就是「整个空间现在哪儿在告警」），没有「跟着窗口走 / 不跟着走」的分工，不必像首页那样拆开。
 */
data class AlarmUiState(
    /** 空间图加载中：服务名与「服务」筛选都靠它，是这一页的骨架 */
    val isLoadingGraph: Boolean = true,
    /** 告警清单取数中 */
    val isLoading: Boolean = false,
    val services: List<ModbusServiceBrief> = emptyList(),
    /** 告警清单；取数失败时为 null */
    val alarms: ModbusAlarmList? = null,
    val error: String? = null,
    /** 正在处理的那条 id（按钮转圈，避免同一条被点两下） */
    val handlingId: String? = null,
    /** 处理失败的原因（就地显示，不动清单） */
    val actionError: String? = null,
    /** 刚处理成功的那条 id（用于一次性的提示，看过即清） */
    val handledId: String? = null,

    // —— 查询条件 ——
    /** 默认最近 24 小时：看告警多是「昨天到今天」的量（与 web 同默认档） */
    val preset: RangePreset = RangePreset.HOUR_24,
    /** 自定义区间的两个绝对时刻（按整天展开，见 CustomRangeDialog）；没选过为空 */
    val customFrom: Long? = null,
    val customTo: Long? = null,
    /** null = 整个项目（不传 serviceId，后端含子空间） */
    val serviceId: String? = null,
    /** null = 所有级别 */
    val level: String? = null,
    /** null = 不限 / false = 只看未处理 / true = 只看已处理 */
    val handled: Boolean? = null,
    /** null = 不限 / false = 只看未恢复 / true = 只看到已恢复 */
    val open: Boolean? = null
) {
    /**
     * 生效的窗口（毫秒）：预设档 = 当下往前推，自定义档 = 用户选的两天。
     *
     * 自定义还没选全时返回 null —— **那时候没有合法窗口可算**，页面据此不出网
     * （后端拒无起点的查询，硬凑一个 now 只会查出个空清单来）。
     */
    val window: Pair<Long, Long>?
        get() = presetWindow(preset, System.currentTimeMillis())
            ?: customFrom?.let { from -> customTo?.let { to -> from to to } }

    /** 窗口内的告警多于 limit：只列了最近的那部分 */
    val truncated: Boolean get() = alarms?.truncated == true
}

/**
 * 阈值告警清单（对齐 webapp-matrix 的 `pages/main/alarm/`）。
 *
 * 骨架与历史页同：服务清单来自空间图（卡片上的服务名与「服务」筛选靠它，查不到时退回 serviceId）。
 * 但**取数只要一条请求** —— 空间级查询一次给出整个子树现在哪儿在告警。
 *
 * **「处理」是就地替换那一行**，不整页刷新：处理只翻一个 `handled` 标志，级别与文本的分布动不了，
 * 故只有「未处理」那个数字要跟着减（怎么减在 [applyHandledAlarm] 里，是纯函数）。
 *
 * `rootId` 是当前项目**根空间**：既是鉴权作用域，也是查询范围（含子空间）。
 */
class AlarmViewModel : ViewModel() {
    private val alarmRepository = ModbusAlarmRepository()
    private val spaceRepository = SpaceRepository()

    private val _uiState = MutableStateFlow(AlarmUiState())
    val uiState: StateFlow<AlarmUiState> = _uiState.asStateFlow()

    /** 记下路由带进来的根空间，之后的刷新与处理都要用它 */
    private var rootId: String? = null

    /**
     * 进页面：先取空间图（拿到服务名），再取告警清单。
     *
     * 空间图失败**不挡住告警清单** —— 服务清单只是「好看」的那一半（名字），清单才是内容：
     * 取不到名字就退回 serviceId，页面照常可用（与 web 同处置）。
     */
    fun load(rootId: String) {
        this.rootId = rootId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingGraph = true)
            val services = spaceRepository.getSpaceGraph(rootId).getOrNull()?.services.orEmpty()
            _uiState.value = _uiState.value.copy(isLoadingGraph = false, services = services)
            loadAlarms()
        }
    }

    /** 按当前条件重算窗口并取数（进页面、切筛选、点「刷新」都走这里） */
    fun loadAlarms() {
        val spaceId = rootId ?: return
        val window = _uiState.value.window ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            alarmRepository.list(
                spaceId = spaceId,
                from = window.first,
                to = window.second,
                query = ModbusAlarmQuery(
                    serviceId = _uiState.value.serviceId,
                    level = _uiState.value.level,
                    handled = _uiState.value.handled,
                    open = _uiState.value.open,
                    limit = ALARM_LIMIT
                )
            )
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(isLoading = false, alarms = list)
                }
                .onFailure { e ->
                    // 失败时把清单清空：留着上一次的结果会让人以为「刚刚刷新过、就是这些」
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        alarms = null,
                        error = e.message ?: "获取告警清单失败"
                    )
                }
        }
    }

    /** 切预设档。选到「自定义」时只换档、不取数 —— 窗口还没定，等用户选完日期 */
    fun setPreset(preset: RangePreset) {
        if (preset == _uiState.value.preset) return
        _uiState.value = _uiState.value.copy(preset = preset)
        if (preset.isPreset) loadAlarms()
    }

    /** 自定义区间选完（两个绝对时刻，已按整天展开） */
    fun setCustomRange(from: Long, to: Long) {
        _uiState.value = _uiState.value.copy(
            preset = RangePreset.CUSTOM,
            customFrom = from,
            customTo = to
        )
        loadAlarms()
    }

    /* 四个筛选各一个入口：传 null 就是「不限」（下拉里单独的那一项） */

    fun setService(serviceId: String?) {
        _uiState.value = _uiState.value.copy(serviceId = serviceId)
        loadAlarms()
    }

    fun setLevel(level: String?) {
        _uiState.value = _uiState.value.copy(level = level)
        loadAlarms()
    }

    fun setHandled(handled: Boolean?) {
        _uiState.value = _uiState.value.copy(handled = handled)
        loadAlarms()
    }

    fun setOpen(open: Boolean?) {
        _uiState.value = _uiState.value.copy(open = open)
        loadAlarms()
    }

    /**
     * 处理一条告警（回执，不是改配置）：后端把「已经处理过了」也当成功返回。
     *
     * 成功后就地替换这一行（不整页刷新）：用户刚点过的那条要立刻变成「已处理」，
     * 否则会以为没生效。
     */
    fun handle(item: ModbusAlarm) {
        val spaceId = rootId ?: return
        val id = item.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(handlingId = id, actionError = null)
            alarmRepository.handle(spaceId, id)
                .onSuccess { updated ->
                    val list = _uiState.value.alarms
                    _uiState.value = _uiState.value.copy(
                        handlingId = null,
                        handledId = id,
                        alarms = list?.let { applyHandledAlarm(it, updated) }
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        handlingId = null,
                        actionError = e.message ?: "处理告警失败"
                    )
                }
        }
    }

    /** 提示看过即清，免得返回这一页时又弹一次 */
    fun clearActionMessage() {
        _uiState.value = _uiState.value.copy(actionError = null, handledId = null)
    }
}
