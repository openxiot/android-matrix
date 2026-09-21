package cc.openxiot.wematrix.ui.modbus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.wematrix.R
import cc.openxiot.wematrix.data.api.ModbusService
import cc.openxiot.wematrix.data.repository.ModbusServiceRepository
import cc.openxiot.wematrix.ui.core.UiText
import cc.openxiot.wematrix.ui.core.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 设备详情页那张「服务」卡片的状态 */
data class DeviceServicesUiState(
    val isLoading: Boolean = true,
    val services: List<ModbusService> = emptyList(),
    val error: UiText? = null
)

/** 服务详情页的状态 */
data class ServiceDetailUiState(
    val isLoading: Boolean = true,
    val service: ModbusService? = null,
    val error: UiText? = null
)

/** 一次调用的结果：哪个方法、方法名、应答解出来的「字段名 → 值」 */
data class InvokeResult(
    val functionIndex: Int,
    val functionName: String,
    val data: Map<String, Any?>
)

/** 调用状态。invokingIndex 非空表示该方法正在调用，此时其余按钮应禁用（对齐 web） */
data class InvokeUiState(
    val invokingIndex: Int? = null,
    val result: InvokeResult? = null,
    val error: UiText? = null
)

/**
 * Modbus 服务：设备详情页的服务卡片与服务详情页（含调用）共用一个 VM。
 *
 * 两处各自 `viewModel()` 得到各自实例（分别绑在各自的导航条目上），互不干扰；
 * 放同一个类里是因为它们共用同一份接口与错误口径，拆开只是重复。
 *
 * 所有方法都要求显式传 `spaceId`（当前项目根空间）—— 它只是后端鉴权作用域，不参与过滤。
 */
class ModbusServiceViewModel : ViewModel() {
    private val repository = ModbusServiceRepository()

    private val _deviceServices = MutableStateFlow(DeviceServicesUiState())
    val deviceServices: StateFlow<DeviceServicesUiState> = _deviceServices.asStateFlow()

    private val _detail = MutableStateFlow(ServiceDetailUiState())
    val detail: StateFlow<ServiceDetailUiState> = _detail.asStateFlow()

    private val _invoke = MutableStateFlow(InvokeUiState())
    val invoke: StateFlow<InvokeUiState> = _invoke.asStateFlow()

    /** 某设备下挂的全部服务（设备详情页那张卡片） */
    fun loadByDevice(spaceId: String, did: String) {
        viewModelScope.launch {
            _deviceServices.value = _deviceServices.value.copy(isLoading = true, error = null)
            repository.listByDevice(spaceId, did)
                .onSuccess { services ->
                    _deviceServices.value = DeviceServicesUiState(
                        isLoading = false,
                        services = services
                    )
                }
                .onFailure { e ->
                    _deviceServices.value = _deviceServices.value.copy(
                        isLoading = false,
                        error = e.toUiText(R.string.err_modbus_service_list)
                    )
                }
        }
    }

    /** 服务完整定义（含方法与请求帧） */
    fun loadDetail(spaceId: String, id: String) {
        viewModelScope.launch {
            _detail.value = _detail.value.copy(isLoading = true, error = null)
            // 换了服务就把上一次的调用结果清掉，免得结果卡挂在另一条服务下面
            _invoke.value = InvokeUiState()
            repository.getService(spaceId, id)
                .onSuccess { service ->
                    _detail.value = ServiceDetailUiState(isLoading = false, service = service)
                }
                .onFailure { e ->
                    _detail.value = _detail.value.copy(
                        isLoading = false,
                        error = e.toUiText(R.string.err_modbus_service_get)
                    )
                }
        }
    }

    /**
     * 调用一个方法：把该方法的请求帧发给依赖设备。
     *
     * 读方法与写方法都放开 —— 写方法的应答是请求回显，返回空表，界面按「设备已收到该帧」提示。
     */
    fun invoke(spaceId: String, serviceId: String, functionIndex: Int) {
        viewModelScope.launch {
            _invoke.value = InvokeUiState(invokingIndex = functionIndex)
            repository.invoke(spaceId, serviceId, functionIndex)
                .onSuccess { data ->
                    val name = _detail.value.service?.functions
                        ?.find { it.index == functionIndex }?.name
                        .orEmpty()
                    _invoke.value = InvokeUiState(
                        invokingIndex = null,
                        result = InvokeResult(functionIndex, name, data)
                    )
                }
                .onFailure { e ->
                    _invoke.value = InvokeUiState(
                        invokingIndex = null,
                        error = e.toUiText(R.string.err_modbus_service_invoke)
                    )
                }
        }
    }

    /** 关掉结果卡 */
    fun clearInvoke() {
        _invoke.value = InvokeUiState()
    }
}
