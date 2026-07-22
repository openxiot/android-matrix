package cc.openxiot.android.ui.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.android.OpenXiotApp
import cc.openxiot.android.data.api.DeviceEntity
import cc.openxiot.android.data.api.DeviceRegistration
import cc.openxiot.android.data.api.RetrofitClient
import cc.openxiot.android.data.api.SpaceEntity
import cc.openxiot.android.data.api.SpaceGraph
import cc.openxiot.android.data.repository.DeviceRepository
import cc.openxiot.android.data.repository.SpaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProjectUiState(
    val isLoading: Boolean = true,
    val rootSpaces: List<SpaceEntity> = emptyList(),
    val currentRootId: String? = null,
    val currentRootName: String? = null,
    val error: String? = null
)

data class SpaceTreeUiState(
    val isLoading: Boolean = true,
    val rootSpace: SpaceEntity? = null,
    val expandedIds: Set<String> = emptySet(),
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val createParentId: String? = null,
    val showDeleteConfirm: String? = null,
    val devices: List<DeviceEntity> = emptyList(),
    val showAddDeviceDialog: Boolean = false,
    val message: String? = null,
    val isAddingDevice: Boolean = false,
    val showMoveDeviceDialog: String? = null,
    val productNames: Map<String, String> = emptyMap()
)

class ProjectViewModel : ViewModel() {
    private val spaceRepository = SpaceRepository()
    private val deviceRepository = DeviceRepository()
    private val tokenManager = OpenXiotApp.instance.tokenManager

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
                        error = e.message
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
                    _projectState.value = _projectState.value.copy(error = e.message)
                }
        }
    }

    fun selectRootSpace(space: SpaceEntity) {
        val rootId = space.id ?: return
        val rootName = space.name ?: rootId
        tokenManager.currentRootSpaceId = rootId
        tokenManager.currentRootSpaceName = rootName
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
                        error = e.message
                    )
                }
        }
    }

    fun loadSpaceGraph(rootId: String) {
        viewModelScope.launch {
            loadSpaceGraphInternal(rootId)
        }
    }

    private val productNameCache = mutableMapOf<String, String>()
    private val productService = RetrofitClient.productService

    suspend fun loadSpaceGraphInternal(rootId: String) {
        _treeState.value = _treeState.value.copy(isLoading = true, error = null)
        spaceRepository.getSpaceGraph(rootId)
            .onSuccess { graph ->
                val root = graph.spaces?.buildTree()
                val devices = graph.devices ?: emptyList()
                _treeState.value = _treeState.value.copy(
                    isLoading = false,
                    error = null,
                    rootSpace = root,
                    devices = devices
                )
                if (_projectState.value.currentRootName == null) {
                    val name = root?.name ?: "项目"
                    _projectState.value = _projectState.value.copy(currentRootName = name)
                    tokenManager.currentRootSpaceName = name
                }
                // Fetch product names for device types
                val orgId = tokenManager.currentOrgId
                if (orgId != null) {
                    val models = devices.mapNotNull { it.type }.filter { it !in productNameCache }.toSet()
                    if (models.isNotEmpty()) {
                        loadProductNames(orgId, models)
                    }
                }
            }
            .onFailure { e ->
                _treeState.value = _treeState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
    }

    private suspend fun loadProductNames(orgId: String, models: Set<String>) {
        models.forEach { model ->
            try {
                val response = productService.getProductByOrgModel(orgId, model)
                if (response.isSuccessful && response.body()?.success == true) {
                    val product = response.body()!!.data
                    val name = product?.displayName ?: model
                    productNameCache[model] = name
                } else {
                    productNameCache[model] = model
                }
            } catch (_: Exception) {
                productNameCache[model] = model
            }
        }
        _treeState.value = _treeState.value.copy(productNames = productNameCache.toMap())
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
                    _treeState.value = _treeState.value.copy(error = e.message)
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
                    _treeState.value = _treeState.value.copy(error = e.message)
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
                    _treeState.value = _treeState.value.copy(error = e.message)
                }
        }
    }

    fun addDeviceByQr(spaceId: String, qrContent: String) {
        viewModelScope.launch {
            _treeState.value = _treeState.value.copy(showAddDeviceDialog = false, isAddingDevice = true)
            deviceRepository.addDeviceByQr(spaceId, qrContent)
                .onSuccess {
                    _projectState.value.currentRootId?.let { loadSpaceGraph(it) }
                    _treeState.value = _treeState.value.copy(isAddingDevice = false, message = "设备添加成功")
                }
                .onFailure { e ->
                    _treeState.value = _treeState.value.copy(isAddingDevice = false, message = "添加设备失败: ${e.message}")
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
                    _treeState.value = _treeState.value.copy(message = "设备已移动")
                }
                .onFailure { e ->
                    _treeState.value = _treeState.value.copy(message = "移动设备失败: ${e.message}")
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
