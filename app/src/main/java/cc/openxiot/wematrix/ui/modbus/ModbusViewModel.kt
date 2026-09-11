package cc.openxiot.wematrix.ui.modbus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.wematrix.data.api.ModbusConfig
import cc.openxiot.wematrix.data.repository.ModbusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ModbusUiState(
    val isLoading: Boolean = true,
    val configs: List<ModbusConfig> = emptyList(),
    val error: String? = null,
    val isDetailLoading: Boolean = false,
    val detail: ModbusConfig? = null,
    val detailError: String? = null
)

class ModbusViewModel : ViewModel() {
    private val repository = ModbusRepository()

    private val _uiState = MutableStateFlow(ModbusUiState())
    val uiState: StateFlow<ModbusUiState> = _uiState.asStateFlow()

    init {
        loadConfigs()
    }

    fun loadConfigs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            var result = repository.getVisibleConfigs()
            // /visible 要组织（X-Org-Id 请求头）。开了「组织」开关不等于选过组织，没有组织时
            // 后端可能直接拒掉，这时退回公开点表——与 web 的 fallbackToPublic 同口径：
            // 宁可只看到公开的，也好过整页报错。
            if (result.isFailure) {
                val fallback = repository.getPublicConfigs()
                if (fallback.isSuccess) result = fallback
            }

            result
                .onSuccess { configs ->
                    _uiState.value = _uiState.value.copy(isLoading = false, configs = configs)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "网络错误"
                    )
                }
        }
    }

    fun loadDetail(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDetailLoading = true, detailError = null)
            repository.getConfig(id)
                .onSuccess { config ->
                    _uiState.value = _uiState.value.copy(isDetailLoading = false, detail = config)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isDetailLoading = false,
                        detailError = e.message ?: "网络错误"
                    )
                }
        }
    }
}
