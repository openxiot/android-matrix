package cc.openxiot.matrix.ui.device.controller

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.api.RetrofitClient
import cc.openxiot.matrix.data.repository.ControlAction
import cc.openxiot.matrix.data.repository.ControlProp
import cc.openxiot.matrix.data.repository.DeviceControlParser
import cc.openxiot.matrix.ui.core.UiText
import cc.openxiot.matrix.ui.core.toUiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 通用设备控制页的状态：拉产品实例 → 解析成卡片（[DeviceControlParser]），实时读写属性、
 * 执行方法。对齐 webapp-matrix `DeviceControllerComponent` 的口径：进入读一次可读属性 +
 * 手动「读取」全量刷新，不做自动轮询。
 *
 * 服务端标签都是数据、原样显示。仅成功/失败反馈这类页面自有文案走资源（[events]）。
 * 反馈文案是**页面自有词**（"已更新" 等），与设备功能标签无关；用 `translatable="false"`
 * 固定中文，是因为它们出现在设备中文标签丛中、却要绕开 60+ 语言目录的翻译管线 —— 这类词
 * 与 device-control 上下文一道按中文主语言落地（见 values/strings.xml 的同名注释）。
 */
data class DeviceControllerUiState(
    val isLoading: Boolean = true,
    val loadError: UiText? = null,
    /** 拍平后的卡片列表 */
    val services: List<cc.openxiot.matrix.data.repository.ControlService> = emptyList(),
    /** 实时属性值：key=`${siid}.${iid}` → value */
    val state: Map<String, Any?> = emptyMap(),
    val isRefreshing: Boolean = false,
    /** 实例取到了但没有可渲染的功能定义 */
    val isEmpty: Boolean = false,
) {
    val serviceCount: Int get() = services.size
}

/** 一次读写/执行的反馈类别（决定 status 0 的成功文案）。 */
private enum class Feedback { UPDATED, READ, EXECUTED }

class DeviceControllerViewModel : ViewModel() {
    private val matrix = RetrofitClient.matrixService
    private val parsed = DeviceControlParser

    private val _uiState = MutableStateFlow(DeviceControllerUiState())
    val uiState: StateFlow<DeviceControllerUiState> = _uiState.asStateFlow()

    /** 一次性反馈（写成功/失败、方法执行结果），由界面 toast。 */
    private val _events = MutableSharedFlow<UiText>(extraBufferCapacity = 4)
    val events: SharedFlow<UiText> = _events.asSharedFlow()

    private var spaceId: String? = null
    private var did: String? = null

    fun load(spaceId: String, did: String, type: String) {
        this.spaceId = spaceId
        this.did = did
        if (type.isBlank()) {
            _uiState.value = _uiState.value.copy(isLoading = false, isEmpty = true)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)
            val data = runCatching {
                val r = RetrofitClient.productService.getProductInstance(type)
                if (r.isSuccessful && r.body()?.success == true) r.body()?.data else null
            }.getOrNull()

            if (data == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadError = UiText.Res(R.string.err_product_detail),
                )
                return@launch
            }

            val services = parsed.parse(data)
            // 可写属性给默认值，让控件有可提交的初始状态（不自动写出）
            val defaults = linkedMapOf<String, Any?>()
            for (svc in services) {
                for (p in svc.props) {
                    if (p.writable && !defaults.containsKey(p.key)) {
                        defaults[p.key] = parsed.defaultValue(p)
                    }
                }
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                services = services,
                state = defaults,
                isEmpty = services.isEmpty(),
            )
            readAll()
        }
    }

    /** 全量刷新：所有可读属性一次读回（手动「读取」按钮与初次进入共用）。 */
    fun refresh() {
        val s = _uiState.value
        if (s.isLoading || s.isRefreshing || s.services.isEmpty()) return
        _uiState.value = s.copy(isRefreshing = true)
        readAll()
    }

    private fun readAll() {
        val space = spaceId ?: return
        val device = did ?: return
        val props = _uiState.value.services.flatMap { it.props }.filter { it.readable }
        if (props.isEmpty()) {
            _uiState.value = _uiState.value.copy(isRefreshing = false)
            return
        }
        viewModelScope.launch {
            runCatching {
                val r = matrix.getDeviceProperties(space, props.map { pidOf(device, it) })
                if (r.isSuccessful && r.body()?.success == true) r.body()?.data.orEmpty() else emptyList()
            }.onSuccess { list ->
                val patch = mutableMapOf<String, Any?>()
                for (item in list) {
                    val key = keyOfPid(item["pid"] as? String, device) ?: continue
                    val status = (item["status"] as? Number)?.toInt() ?: 0
                    if (status == 0) patch[key] = item["value"]
                }
                if (patch.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(state = _uiState.value.state + patch)
                }
            }.onFailure { e ->
                _events.emit(e.toUiText(R.string.err_response_empty))
            }
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    /** 读单个属性（读按钮）。 */
    fun read(p: ControlProp) {
        val space = spaceId ?: return
        val device = did ?: return
        viewModelScope.launch {
            runCatching {
                val r = matrix.getDeviceProperties(space, listOf(pidOf(device, p)))
                if (r.isSuccessful && r.body()?.success == true) r.body()?.data.orEmpty() else emptyList()
            }.onSuccess { list ->
                val item = list.firstOrNull() ?: return@onSuccess
                val key = keyOfPid(item["pid"] as? String, device) ?: return@onSuccess
                val status = (item["status"] as? Number)?.toInt() ?: 0
                if (status == 0) {
                    _uiState.value = _uiState.value.copy(
                        state = _uiState.value.state + (key to item["value"])
                    )
                }
                feedback(Feedback.READ, status, p.name)
            }.onFailure { e -> _events.emit(e.toUiText(R.string.err_response_empty)) }
        }
    }

    /** 写一个属性（本地先改状态，服务端按 status 反馈）。 */
    fun write(p: ControlProp, value: Any?) {
        val space = spaceId ?: return
        val device = did ?: return
        val coerced = coerceValue(p, value)
        _uiState.value = _uiState.value.copy(state = _uiState.value.state + (p.key to coerced))
        viewModelScope.launch {
            runCatching {
                val body = mapOf("pid" to pidOf(device, p), "value" to coerced)
                val r = matrix.setDeviceProperties(space, listOf(body))
                if (r.isSuccessful && r.body()?.success == true) r.body()?.data.orEmpty() else emptyList()
            }.onSuccess { list ->
                val item = list.firstOrNull()
                feedback(Feedback.UPDATED, (item?.get("status") as? Number)?.toInt() ?: 0, p.name)
            }.onFailure { e -> _events.emit(e.toUiText(R.string.err_response_empty)) }
        }
    }

    /** 信息卡「点亮设备」。 */
    fun identify(p: ControlProp) {
        write(p, true)
    }

    /**
     * 执行方法。[values] 按 [a.args] 顺序提供，缺失的入参不下发。
     * 方法返回的 out 值回写到对应属性状态。
     */
    fun invoke(a: ControlAction, values: List<Any?>) {
        val space = spaceId ?: return
        val device = did ?: return
        val inArgs = a.args.mapIndexedNotNull { i, arg ->
            val v = values.getOrNull(i) ?: return@mapIndexedNotNull null
            mapOf("piid" to arg.piid, "values" to listOf(v))
        }
        viewModelScope.launch {
            runCatching {
                val body = mapOf("aid" to "${device}.${a.siid}.${a.iid}", "in" to inArgs)
                val r = matrix.invokeDeviceAction(space, listOf(body))
                if (r.isSuccessful && r.body()?.success == true) r.body()?.data.orEmpty() else emptyList()
            }.onSuccess { list ->
                val item = list.firstOrNull()
                val status = (item?.get("status") as? Number?)?.toInt()
                applyOut(a.siid, item?.get("out"))
                when (status) {
                    1 -> feedback0(Feedback.EXECUTED, pending = true, a.name)
                    else -> feedback(Feedback.EXECUTED, status ?: 0, a.name)
                }
            }.onFailure { e -> _events.emit(e.toUiText(R.string.err_response_empty)) }
        }
    }

    /** 方法 out（{piid,values}[] 或 {#piid:values}）回写到对应属性 state。 */
    private fun applyOut(siid: Int, out: Any?) {
        if (out == null) return
        val patch = mutableMapOf<String, Any?>()
        when (out) {
            is List<*> -> for (o in out) {
                if (o !is Map<*, *>) continue
                val piid = (o["piid"] as? Number)?.toInt() ?: continue
                val v = (o["values"] as? List<*>)?.firstOrNull() ?: continue
                patch["$siid.$piid"] = v
            }
            is Map<*, *> -> for ((k, v) in out) {
                val piidVal = (k as? String)?.removePrefix("#")?.toIntOrNull() ?: continue
                val first = (v as? List<*>)?.firstOrNull() ?: continue
                patch["$siid.$piidVal"] = first
            }
        }
        if (patch.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(state = _uiState.value.state + patch)
        }
    }

    // ===================== 反馈 =====================

    private fun pidOf(device: String, p: ControlProp): String = "$device.${p.siid}.${p.iid}"

    /**
     * 文本输入框回传的是 String；数值属性在写出前转成 Number（否则服务端取值不合法）。
     * 其余类型（开关已 bool、枚举已原类型、步进已 Double、string/hex 本就文本）原样。
     */
    private fun coerceValue(p: ControlProp, value: Any?): Any? {
        if (value !is String) return value
        return when (p.format) {
            "int8", "int16", "int32", "uint8", "uint16", "float", "double" ->
                value.toDoubleOrNull() ?: value
            else -> value
        }
    }

    /** pid（"did.siid.iid"）反解回状态键。按 did 前缀切、不按 `.` 分段（did 可能带点）。 */
    private fun keyOfPid(pid: String?, device: String): String? {
        if (pid == null || !pid.startsWith("$device.")) return null
        val rest = pid.removePrefix("$device.")
        val dot = rest.indexOf('.')
        if (dot <= 0) return null
        val siid = rest.substring(0, dot).toIntOrNull() ?: return null
        val iid = rest.substring(dot + 1).toIntOrNull() ?: return null
        return "$siid.$iid"
    }

    private fun feedback(kind: Feedback, status: Int, name: String) {
        if (status == 0) {
            feedback0(kind, pending = false, name)
            return
        }
        if (status == 1) {
            feedback0(kind, pending = true, name)
            return
        }
        // 非 0/1 的负状态码：带名字 + 状态文案
        viewModelScope.launch {
            _events.emit(
                UiText.Res(
                    R.string.dc_status_fmt,
                    listOf(UiText.Raw(name), UiText.Res(statusTextRes(status))),
                )
            )
        }
    }

    private fun feedback0(kind: Feedback, pending: Boolean, name: String) {
        val res = when {
            kind == Feedback.UPDATED -> R.string.dc_updated
            kind == Feedback.READ -> R.string.dc_read
            pending -> R.string.dc_executed_pending
            else -> R.string.dc_executed
        }
        viewModelScope.launch { _events.emit(UiText.Res(res, listOf(UiText.Raw(name)))) }
    }

    @StringRes
    private fun statusTextRes(status: Int): Int = when (status) {
        -1 -> R.string.dc_status_cannot_read
        -2 -> R.string.dc_status_cannot_write
        -3 -> R.string.dc_status_not_found
        -4 -> R.string.dc_status_internal
        -5 -> R.string.dc_status_value_invalid
        -6 -> R.string.dc_status_arg_invalid
        -7 -> R.string.dc_status_verify_fail
        else -> R.string.err_unknown
    }
}