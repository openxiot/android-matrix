package cc.openxiot.wematrix.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.wematrix.data.repository.ModbusRepository
import cc.openxiot.wematrix.data.repository.StatisticsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 首页看板的 UI 状态。
 *
 * **没取到就是 null，不是一份全零的默认值** —— 0 是有效读数（项目里真没有设备），
 * 拿它冒充「没数据」会让卡片信誓旦旦地报一个假数；null 在页面上是空白。
 * 这条与 web 的 `OverviewStatistics | null` 同一个口径（见 dashboard.component.ts 的注释）。
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    /** 取数失败的原因；非空时整页只显示这条告警 */
    val error: String? = null,
    /** 设备总量（含子空间） */
    val deviceTotal: Int? = null,
    /** 在线设备（后端按 `device.online` 数），挂在「设备总量」卡的标题行右端 */
    val onlineCount: Int? = null,
    /** 服务总量（含子空间） */
    val serviceTotal: Int? = null,
    /** 今日告警：本地今天 00:00 起的告警条数 */
    val alarmToday: Int? = null,
    /** 设备类型分布（按 URN 类型段） */
    val deviceTypes: List<ChartPoint> = emptyList(),
    /** 服务类型分布（按所配点表，同名已合并） */
    val serviceTypes: List<ChartPoint> = emptyList(),
    /** 告警类型分布（按用户填的告警文本） */
    val alarmTexts: List<ChartPoint> = emptyList(),
    /** 告警曲线：近 24 小时整点，桶名 `HH:00` */
    val alarmHourly: List<ChartPoint> = emptyList(),
    /** 本月能耗（kWh，mock） */
    val monthEnergy: Int = 0,
    /** 日能耗曲线（mock） */
    val energyDaily: List<DailyEnergy> = emptyList()
) {
    // 三张饼的空数据判定（空饼画出来是一片空白，不如明说「没有数据」）。
    // 两条曲线不吃这一套：后端密集零填充，桶永远在，全 0 就是全 0
    val hasDeviceTypes: Boolean get() = deviceTypes.isNotEmpty()
    val hasServiceTypes: Boolean get() = serviceTypes.isNotEmpty()
    val hasAlarmTexts: Boolean get() = alarmTexts.isNotEmpty()
}

/**
 * 首页（数据看板）。
 *
 * 一屏的四个数字与四张图分两处取：
 * - **真实**：设备（总量，在线数挂在同一张卡的标题行）、服务（总量 / 按点表）、
 *   告警（今日 / 近 24 小时 / 按文本）—— 全来自一次 `GET /statistics/overview`，
 *   故卡片与曲线天然同源；
 * - **伪造**：本月能耗与日能耗曲线（没有能耗采集，见 [HomeMock]）。
 *
 * 窗口由本页算（后端只收 from/to）：近 24 小时整点，见 [alarmWindow]。「今日」= 窗口内
 * 桶起点不早于本地今天 00:00 的求和 —— 窗口恒盖住今天全天，不必第二次请求。
 *
 * 未选项目时不出网、不显示全 0（页面走空态）；接口失败**要报错、不能静默显示 0**
 * —— 看板上一个静默的 0 会被读成「项目里一个设备都没有」。
 */
class HomeViewModel : ViewModel() {
    private val statisticsRepository = StatisticsRepository()
    private val modbusRepository = ModbusRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 能耗是 mock、与项目无关：同一天内稳定，跨天自然变化 */
    private val energy: EnergyStats = mockEnergyStats(LocalDate.now())

    /**
     * 取整页的数。每次进首页都会调一次（切 Tab 回来时也重取）：接口是只读的、代价一次请求，
     * 比让用户看一份可能已经过期的数划算。
     */
    fun load(rootId: String?) {
        if (rootId.isNullOrEmpty()) {
            // 未选项目：页面走空态，别发一个注定 403 的请求
            _uiState.value = HomeUiState(
                isLoading = false,
                monthEnergy = energy.monthTotal,
                energyDaily = energy.daily
            )
            return
        }

        viewModelScope.launch {
            // 已经有数就不打转（切 Tab 回来的重取），免得整页闪一下空白；首次进页面才有转圈
            _uiState.value = _uiState.value.copy(
                isLoading = _uiState.value.deviceTotal == null,
                error = null
            )

            val (from, to) = alarmWindow(System.currentTimeMillis())
            val overviewResult = statisticsRepository.overview(rootId, from, to)
            val overview = overviewResult.getOrElse { e ->
                _uiState.value = HomeUiState(
                    isLoading = false,
                    error = e.message ?: "网络错误",
                    monthEnergy = energy.monthTotal,
                    energyDaily = energy.daily
                )
                return@launch
            }

            // 点表清单只用于把 configId 解成显示名，取不到就退回 id（与服务清单页同口径），
            // 不该拖垮整页 —— 故这里兜错与上面那条分开
            val configs = modbusRepository.getVisibleConfigs().getOrDefault(emptyList())

            // 「今日」的基准取响应里实际生效的窗口终点，而不是本地 now：两者差几毫秒，
            // 而桶是整点的，跨零点那一瞬间用本地时间会算到「昨天」。
            // 后端一定会回显 to；真拿到 0（老版本）就退回本地算的那个，免得 dayStart(0) 把 24 个桶全算成今天
            val alarmBase = if (overview.to > 0) overview.to else to

            _uiState.value = HomeUiState(
                isLoading = false,
                deviceTotal = overview.devices.total,
                onlineCount = overview.devices.online,
                serviceTotal = overview.services.total,
                alarmToday = sumSince(overview.alarms.hourly, dayStart(alarmBase)),
                deviceTypes = distributionData(overview.devices.byType),
                serviceTypes = serviceTypeData(overview.services.byConfig, configs, "未定义"),
                alarmTexts = distributionData(overview.alarms.byText),
                alarmHourly = hourLabels(overview.alarms.hourly)
                    .zip(overview.alarms.hourly) { label, bucket -> ChartPoint(label, bucket.count) },
                monthEnergy = energy.monthTotal,
                energyDaily = energy.daily
            )
        }
    }
}
