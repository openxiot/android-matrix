package cc.openxiot.matrix.ui.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.matrix.R
import cc.openxiot.matrix.MatrixApp
import cc.openxiot.matrix.data.api.DeviceEntity
import cc.openxiot.matrix.data.api.DeviceRegistration
import cc.openxiot.matrix.data.api.ModbusServiceBrief
import cc.openxiot.matrix.data.api.RetrofitClient
import cc.openxiot.matrix.data.api.SpaceEntity
import cc.openxiot.matrix.data.repository.DeviceRepository
import cc.openxiot.matrix.data.repository.SpaceRepository
import cc.openxiot.matrix.ui.core.SessionState
import cc.openxiot.matrix.ui.core.UiText
import cc.openxiot.matrix.ui.core.toUiText
import cc.openxiot.matrix.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProjectUiState(
    val isLoading: Boolean = true,
    val rootSpaces: List<SpaceEntity> = emptyList(),
    val currentRootId: String? = null,
    val currentRootName: String? = null,
    val error: UiText? = null
)

/** 标准 IMEI 为 15 位纯数字（如 864317084946840） */
private val IMEI_REGEX = Regex("""^\d{15}$""")

data class SpaceTreeUiState(
    val isLoading: Boolean = true,
    val rootSpace: SpaceEntity? = null,
    val expandedIds: Set<String> = emptySet(),
    /**
     * 展开的设备（did）。与 [expandedIds]（空间 id）**分开存**：两者都是字符串，
     * 混在一个 Set 里存在空间 id 和设备 did 撞键的风险（web 靠 `space:` / `device:` 前缀规避）。
     */
    val expandedDeviceIds: Set<String> = emptySet(),
    val error: UiText? = null,
    val showCreateDialog: Boolean = false,
    val createParentId: String? = null,
    val showDeleteConfirm: String? = null,
    val devices: List<DeviceEntity> = emptyList(),
    /** 空间图里带回来的 Modbus 服务（精简视图）：按 did 挂到设备节点下 */
    val services: List<ModbusServiceBrief> = emptyList(),
    val showAddDeviceDialog: Boolean = false,
    val message: UiText? = null,
    val isAddingDevice: Boolean = false,
    val showMoveDeviceDialog: String? = null,
    val productNames: Map<String, String?> = emptyMap(),
    val productIcons: Map<String, String> = emptyMap()
)

class ProjectViewModel : ViewModel() {
    private val spaceRepository = SpaceRepository()
    private val deviceRepository = DeviceRepository()
    private val tokenManager = MatrixApp.instance.tokenManager

    private val _projectState = MutableStateFlow(ProjectUiState(
        currentRootId = tokenManager.currentRootSpaceId
    ))
    val projectState: StateFlow<ProjectUiState> = _projectState.asStateFlow()

    private val _treeState = MutableStateFlow(SpaceTreeUiState())
    val treeState: StateFlow<SpaceTreeUiState> = _treeState.asStateFlow()

    init {
        // Eagerly load space graph if a project is already selected
        tokenManager.currentRootSpaceId?.let { loadSpaceGraph(it) }
    }

    fun loadRootSpaces() {
        viewModelScope.launch {
            _projectState.value = _projectState.value.copy(isLoading = true, error = null)
            spaceRepository.getAllSpaces()
                .onSuccess { spaces ->
                    _projectState.value = _projectState.value.copy(
                        isLoading = false,
                        rootSpaces = spaces
                    )
                }
                .onFailure { e ->
                    _projectState.value = _projectState.value.copy(
                        isLoading = false,
                        error = e.toUiText()
                    )
                }
        }
    }

    fun renameRootSpace(spaceId: String, newName: String) {
        viewModelScope.launch {
            spaceRepository.updateSpace(SpaceEntity(id = spaceId, name = newName))
                .onSuccess {
                    loadRootSpaces()
                    if (_projectState.value.currentRootId == spaceId) {
                        _projectState.value = _projectState.value.copy(currentRootName = newName)
                        tokenManager.currentRootSpaceId = spaceId
                    }
                }
                .onFailure { e ->
                    _projectState.value = _projectState.value.copy(error = e.toUiText())
                }
        }
    }

    fun selectRootSpace(space: SpaceEntity) {
        val rootId = space.id ?: return
        val rootName = space.name ?: rootId
        // 走 SessionState：持久值 + 响应式值一起写，否则切项目回来看板不会刷新
        SessionState.setRootSpace(rootId, rootName)
        _projectState.value = _projectState.value.copy(
            currentRootId = rootId,
            currentRootName = rootName
        )
        loadSpaceGraph(rootId)
    }

    fun loadSpaceTree(rootId: String) {
        viewModelScope.launch {
            _treeState.value = SpaceTreeUiState(isLoading = true)
            spaceRepository.getSpaceTree(rootId)
                .onSuccess { root ->
                    _treeState.value = _treeState.value.copy(
                        isLoading = false,
                        rootSpace = root
                    )
                }
                .onFailure { e ->
                    _treeState.value = _treeState.value.copy(
                        isLoading = false,
                        error = e.toUiText()
                    )
                }
        }
    }

    fun loadSpaceGraph(rootId: String) {
        viewModelScope.launch {
            loadSpaceGraphInternal(rootId)
        }
    }

    private val productNameCache = mutableMapOf<String, String?>()
    private val productIconCache = mutableMapOf<String, String>()
    private val productService = RetrofitClient.productService

    suspend fun loadSpaceGraphInternal(rootId: String) {
        _treeState.value = _treeState.value.copy(isLoading = true, error = null)
        spaceRepository.getSpaceGraph(rootId)
            .onSuccess { graph ->
                val root = graph.spaces?.buildTree()
                // 管理员态：根空间 access 里挂了一条 admin（组织或账号）即给编辑权。
                // 客户端只是隐藏入口，服务端 PUT 仍强制管理员，两处不冲突。
                root?.accesses?.let { accesses ->
                    SessionState.setCanEdit(rootId, accesses.any { it.role == "admin" })
                }
                val devices = graph.devices ?: emptyList()
                _treeState.value = _treeState.value.copy(
                    isLoading = false,
                    error = null,
                    rootSpace = root,
                    devices = devices,
                    services = graph.services ?: emptyList()
                )
                if (_projectState.value.currentRootName == null) {
                    val name = root?.name ?: MatrixApp.instance.getString(R.string.tab_project)
                    _projectState.value = _projectState.value.copy(currentRootName = name)
                    tokenManager.currentRootSpaceName = name
                }
                // Fetch product names for all device types
                val orgModels = devices.mapNotNull { extractOrgModel(it.type) }.toSet()
                val orgId = tokenManager.currentOrgId
                if (orgModels.isNotEmpty()) {
                    loadProductNames(orgModels)
                }
                if (orgId != null) {
                    loadAllProductNames(orgId)
                }
            }
            .onFailure { e ->
                _treeState.value = _treeState.value.copy(
                    isLoading = false,
                    error = e.toUiText()
                )
            }
    }

    private suspend fun loadProductNames(orgModels: Set<Pair<String, String>>) {
        for ((org, model) in orgModels) {
            try {
                val response = productService.getProductByOrgModel(org, model)
                if (response.isSuccessful && response.body()?.success == true) {
                    val product = response.body()!!.data ?: continue
                    productNameCache[model] = product.displayName
                    product.icon?.let { productIconCache[model] = it }
                }
            } catch (_: Exception) { }
        }
        _treeState.value = _treeState.value.copy(
            productNames = productNameCache.toMap(),
            productIcons = productIconCache.toMap()
        )
    }

    private suspend fun loadAllProductNames(orgId: String) {
        try {
            val response = productService.getVisibleProducts(orgId)
            if (response.isSuccessful && response.body()?.success == true) {
                val products = response.body()!!.data ?: emptyList()
                products.forEach { product ->
                    product.model?.let { model ->
                        if (model !in productNameCache) {
                            productNameCache[model] = product.displayName
                        }
                        if (product.icon != null && model !in productIconCache) {
                            productIconCache[model] = product.icon
                        }
                    }
                }
                _treeState.value = _treeState.value.copy(
                    productNames = productNameCache.toMap(),
                    productIcons = productIconCache.toMap()
                )
            }
        } catch (_: Exception) { }
    }

    private fun extractOrgModel(urn: String?): Pair<String, String>? {
        if (urn == null) return null
        val parts = urn.split(":")
        return if (parts.size >= 7) parts[5] to parts[6] else null
    }

    fun toggleExpanded(spaceId: String) {
        val current = _treeState.value.expandedIds.toMutableSet()
        if (current.contains(spaceId)) {
            current.remove(spaceId)
        } else {
            current.add(spaceId)
        }
        _treeState.value = _treeState.value.copy(expandedIds = current)
    }

    /** 设备卡片（空间树 / 设备列表）的展开收起，键是 did */
    fun toggleDeviceExpanded(deviceId: String) {
        val current = _treeState.value.expandedDeviceIds.toMutableSet()
        if (!current.remove(deviceId)) {
            current.add(deviceId)
        }
        _treeState.value = _treeState.value.copy(expandedDeviceIds = current)
    }

    fun showCreateDialog(parentId: String?) {
        _treeState.value = _treeState.value.copy(
            showCreateDialog = true,
            createParentId = parentId
        )
    }

    fun hideCreateDialog() {
        _treeState.value = _treeState.value.copy(
            showCreateDialog = false,
            createParentId = null
        )
    }

    fun createSpace(name: String, type: String, parentId: String?, rootId: String?) {
        viewModelScope.launch {
            _treeState.value = _treeState.value.copy(showCreateDialog = false)
            val space = SpaceEntity(
                name = name,
                type = type,
                parentId = parentId,
                rootId = rootId,
                sortOrder = 0
            )
            spaceRepository.createSpace(space)
                .onSuccess {
                    if (rootId != null) loadSpaceGraph(rootId)
                    else loadRootSpaces()
                }
                .onFailure { e ->
                    _treeState.value = _treeState.value.copy(error = e.toUiText())
                }
        }
    }

    fun showDeleteConfirm(spaceId: String) {
        _treeState.value = _treeState.value.copy(showDeleteConfirm = spaceId)
    }

    fun hideDeleteConfirm() {
        _treeState.value = _treeState.value.copy(showDeleteConfirm = null)
    }

    fun deleteSpace(spaceId: String, rootId: String?) {
        viewModelScope.launch {
            _treeState.value = _treeState.value.copy(showDeleteConfirm = null)
            spaceRepository.deleteSpace(spaceId)
                .onSuccess {
                    if (rootId != null) loadSpaceGraph(rootId)
                    else loadRootSpaces()
                }
                .onFailure { e ->
                    _treeState.value = _treeState.value.copy(error = e.toUiText())
                }
        }
    }

    fun showAddDeviceDialog() {
        _treeState.value = _treeState.value.copy(showAddDeviceDialog = true)
    }

    fun hideAddDeviceDialog() {
        _treeState.value = _treeState.value.copy(showAddDeviceDialog = false)
    }

    fun addDevice(spaceId: String, did: String, type: String) {
        viewModelScope.launch {
            _treeState.value = _treeState.value.copy(showAddDeviceDialog = false)
            deviceRepository.addDevices(spaceId, listOf(DeviceRegistration(did, type)))
                .onSuccess {
                    _projectState.value.currentRootId?.let { loadSpaceGraph(it) }
                }
                .onFailure { e ->
                    _treeState.value = _treeState.value.copy(error = e.toUiText())
                }
        }
    }

    /**
     * 扫码添加设备。特例：若扫到的是纯数字 IMEI（如 864317084946840），
     * 先经 DTU 网关查询 DID，再以 did + key 登记设备，其中 key 取 IMEI 的值。
     */
    fun addDeviceByQr(spaceId: String, qrContent: String) {
        viewModelScope.launch {
            _treeState.value = _treeState.value.copy(showAddDeviceDialog = false, isAddingDevice = true)
            val content = qrContent.trim()
            val result = if (IMEI_REGEX.matches(content)) {
                deviceRepository.addDeviceByImei(spaceId, content, Constants.DTU_ORG_ID)
            } else {
                deviceRepository.addDeviceByQr(spaceId, content)
            }
            result
                .onSuccess {
                    _projectState.value.currentRootId?.let { loadSpaceGraph(it) }
                    _treeState.value = _treeState.value.copy(
                        isAddingDevice = false,
                        message = UiText.Res(R.string.project_device_added)
                    )
                }
                .onFailure { e ->
                    _treeState.value = _treeState.value.copy(
                        isAddingDevice = false,
                        message = UiText.Res(R.string.err_project_device_add, listOf(e.toUiText()))
                    )
                }
        }
    }

    fun showMoveDevice(did: String) {
        _treeState.value = _treeState.value.copy(showMoveDeviceDialog = did)
    }

    fun hideMoveDevice() {
        _treeState.value = _treeState.value.copy(showMoveDeviceDialog = null)
    }

    fun moveDeviceTo(spaceId: String, did: String) {
        viewModelScope.launch {
            val rootId = _projectState.value.currentRootId ?: return@launch
            _treeState.value = _treeState.value.copy(showMoveDeviceDialog = null)
            deviceRepository.moveDevice(spaceId, rootId, did)
                .onSuccess {
                    loadSpaceGraph(rootId)
                    _treeState.value = _treeState.value.copy(message = UiText.Res(R.string.project_device_moved))
                }
                .onFailure { e ->
                    _treeState.value = _treeState.value.copy(
                        message = UiText.Res(R.string.err_project_device_move, listOf(e.toUiText()))
                    )
                }
        }
    }

    fun clearTreeMessage() {
        _treeState.value = _treeState.value.copy(message = null)
    }

    fun clearProjectError() {
        _projectState.value = _projectState.value.copy(error = null)
    }

    fun clearTreeError() {
        _treeState.value = _treeState.value.copy(error = null)
    }

    fun setCurrentRootName(name: String) {
        _projectState.value = _projectState.value.copy(currentRootName = name)
    }
}

/**
 * Build a nested tree from a flat list of spaces using parentId relationships.
 * The first element is treated as the root.
 */
private fun List<SpaceEntity>.buildTree(): SpaceEntity? {
    if (isEmpty()) return null
    val byParentId = groupBy { it.parentId }

    fun buildChildren(parentId: String?): List<SpaceEntity> {
        return byParentId[parentId]?.map { space ->
            space.copy(children = buildChildren(space.id))
        } ?: emptyList()
    }

    val root = first()
    return root.copy(children = buildChildren(root.id))
}
