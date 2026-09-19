package cc.openxiot.wematrix.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.wematrix.data.api.MobileDashboardWidget
import cc.openxiot.wematrix.data.repository.MobileDashboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 首页看板的 UI 状态。
 *
 * `widgets` 是布局顺序（= 阅读顺序）；`dataById` / `messageById` 把取了数的每张卡按 id 收在一起
 * —— **不按下标对应**（顺序会随编辑保存变化，取数结果按下标取就会张冠李戴）。
 * 某张卡失败进 `messageById`（服务端原文），它自己的 `data` 为空；**一张卡失败不拖垮整屏**。
 */
data class MobileDashboardUiState(
    val isLoading: Boolean = false,
    /** 整页级失败（布局/取数全挂）：非空时整页只显示这条告警 */
    val error: String? = null,
    val widgets: List<MobileDashboardWidget> = emptyList(),
    val dataById: Map<String, Map<String, Any?>> = emptyMap(),
    val messageById: Map<String, String> = emptyMap()
)

/**
 * 首页（可自定义看板）。
 *
 * 数据流：`GET /layout/{spaceId}` 取布局（从未保存时后端直接返回预置，version=0）→
 * `POST /render` 取每张卡的数据。两次都是只读、按当前项目根空间鉴权。
 *
 * **没取到 / 未选项目都是空态**，不放一屏 0 —— 0 是有效读数（项目里真没设备），
 * 拿它冒充「没数据」会让看板信誓旦旦报假数。
 */
class MobileDashboardViewModel : ViewModel() {
    private val repository = MobileDashboardRepository()

    private val _uiState = MutableStateFlow(MobileDashboardUiState())
    val uiState: StateFlow<MobileDashboardUiState> = _uiState.asStateFlow()

    /**
     * 取整页：布局 + 每张卡的取数。每次进首页都重取（切 Tab 回来也取一次）：
     * 接口只读、代价几次请求，比让用户看一份可能过期的数划算。
     */
    fun load(rootId: String?) {
        if (rootId.isNullOrEmpty()) {
            _uiState.value = MobileDashboardUiState()
            return
        }

        viewModelScope.launch {
            // 已有布局就不打转（切 Tab 回来的重取），免得整页闪一下空白
            _uiState.value = _uiState.value.copy(
                isLoading = _uiState.value.widgets.isEmpty(),
                error = null
            )

            val layout = repository.getLayout(rootId).getOrElse { e ->
                _uiState.value = MobileDashboardUiState(error = e.message ?: "网络错误")
                return@launch
            }

            val result = repository.render(rootId, layout.widgets).getOrElse { e ->
                _uiState.value = MobileDashboardUiState(
                    widgets = layout.widgets,
                    error = e.message ?: "取数失败"
                )
                return@launch
            }

            val dataById = HashMap<String, Map<String, Any?>>()
            val messageById = HashMap<String, String>()
            result.widgets.forEach { item ->
                if (item.success && item.data != null) {
                    dataById[item.id.orEmpty()] = item.data
                } else {
                    item.id?.let { messageById[it] = item.message ?: "取数失败" }
                }
            }
            _uiState.value = MobileDashboardUiState(
                isLoading = false,
                widgets = layout.widgets,
                dataById = dataById,
                messageById = messageById
            )
        }
    }
}