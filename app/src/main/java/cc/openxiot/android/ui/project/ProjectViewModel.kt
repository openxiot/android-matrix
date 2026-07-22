package cc.openxiot.android.ui.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.android.OpenXiotApp
import cc.openxiot.android.data.api.DeviceEntity
import cc.openxiot.android.data.api.DeviceRegistration
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
    val showAddDeviceDialog: Boolean = false
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
        loadSpaceTree(rootId)
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

    suspend fun loadSpaceGraphInternal(rootId: String) {
        _treeState.value = _treeState.value.copy(isLoading = true)
        spaceRepository.getSpaceGraph(rootId)
            .onSuccess { graph ->
                val root = graph.spaces?.buildTree()
                _treeState.value = _treeState.value.copy(
                    isLoading = false,
                    rootSpace = root,
                    devices = graph.devices ?: emptyList()
                )
            }
            .onFailure { e ->
                _treeState.value = _treeState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
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
                    if (rootId != null) loadSpaceTree(rootId)
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
                    if (rootId != null) loadSpaceTree(rootId)
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
