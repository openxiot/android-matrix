package cc.openxiot.matrix.ui.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.matrix.MatrixApp
import cc.openxiot.matrix.data.api.ProjectMember
import cc.openxiot.matrix.data.repository.OrganizationRepository
import cc.openxiot.matrix.data.repository.SpaceRepository
import cc.openxiot.matrix.ui.core.UiText
import cc.openxiot.matrix.ui.core.toUiText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProjectMemberUiState(
    val isLoading: Boolean = true,
    val projectName: String? = null,
    val members: List<ProjectMember> = emptyList(),
    val currentUserId: String? = null,
    val isAdmin: Boolean = false,
    val isLastAdmin: Boolean = false,
    val error: UiText? = null,
    val showAddDialog: Boolean = false,
    val changeRoleTarget: ProjectMember? = null,
    val editRemarkTarget: ProjectMember? = null,
    val removeTarget: ProjectMember? = null,
    val showLeaveConfirm: Boolean = false,
    val hasLeft: Boolean = false
)

/**
 * 项目（根空间）成员管理。与 webapp ProjectMemberComponent 对齐：
 * - isAdmin：自己是 user admin，或组织兜底（当前组织命中该空间 organization 条目且本人为组织管理员）
 * - isLastAdmin：自己是 user admin 且成员列表无其他 user admin（不做组织兜底）
 * 最后管理员防护由后端强制，前端仅控制按钮显隐。
 */
class ProjectMemberViewModel : ViewModel() {
    private val spaceRepository = SpaceRepository()
    private val organizationRepository = OrganizationRepository()
    private val tokenManager = MatrixApp.instance.tokenManager

    private val _uiState = MutableStateFlow(
        ProjectMemberUiState(currentUserId = tokenManager.developerId)
    )
    val uiState: StateFlow<ProjectMemberUiState> = _uiState.asStateFlow()

    fun load(rootId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, hasLeft = false)
            if (_uiState.value.currentUserId == null) {
                tokenManager.developerId = tokenManager.extractDeveloperIdFromToken()
                _uiState.value = _uiState.value.copy(currentUserId = tokenManager.developerId)
            }

            coroutineScope {
                // 并行加载空间（accesses 含 organization 条目）、当前组织（成员角色）、项目成员列表
                val spaceDeferred = async { spaceRepository.getSpace(rootId).getOrNull() }
                val orgDeferred = async {
                    tokenManager.currentOrgId?.let { organizationRepository.getOrganization(it).getOrNull() }
                }

                spaceRepository.listAccesses(rootId)
                    .onSuccess { members ->
                        val space = spaceDeferred.await()
                        val org = orgDeferred.await()
                        val currentUserId = _uiState.value.currentUserId

                        val self = members.find { it.userId == currentUserId }
                            ?: members.firstOrNull { it.name == tokenManager.username }

                        // isAdmin：项目角色 admin，或组织兜底
                        val projectAdmin = self?.role == "admin"
                        val orgEntryMatched = space?.accesses?.any {
                            it.type == "organization" && it.id == tokenManager.currentOrgId
                        } == true
                        val orgAdmin = org?.members?.any {
                            it.developerId == currentUserId && it.role == "admin"
                        } == true
                        val isAdmin = projectAdmin || (orgEntryMatched && orgAdmin)

                        // isLastAdmin：自己是 user admin 且无其他 user admin（无组织兜底）
                        val isLastAdmin = self?.role == "admin" &&
                            !members.any { it.userId != self.userId && it.role == "admin" }

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            projectName = space?.name ?: _uiState.value.projectName,
                            members = members,
                            currentUserId = currentUserId,
                            isAdmin = isAdmin,
                            isLastAdmin = isLastAdmin,
                            error = null
                        )
                    }
                    .onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = e.toUiText()
                        )
                    }
            }
        }
    }

    // ---- 添加成员 ----

    fun showAddMemberDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun hideAddMemberDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun addMember(rootId: String, memberId: String, role: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showAddDialog = false)
            spaceRepository.addAccess(rootId, memberId, role)
                .onSuccess { load(rootId) }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.toUiText())
                }
        }
    }

    // ---- 调整角色 ----

    fun showChangeRoleDialog(member: ProjectMember) {
        _uiState.value = _uiState.value.copy(changeRoleTarget = member)
    }

    fun hideChangeRoleDialog() {
        _uiState.value = _uiState.value.copy(changeRoleTarget = null)
    }

    fun updateRole(rootId: String, member: ProjectMember, newRole: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(changeRoleTarget = null)
            val memberId = member.userId ?: return@launch
            spaceRepository.updateAccessRole(rootId, memberId, newRole)
                .onSuccess { load(rootId) }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.toUiText())
                }
        }
    }

    // ---- 编辑备注 ----

    fun showEditRemarkDialog(member: ProjectMember) {
        _uiState.value = _uiState.value.copy(editRemarkTarget = member)
    }

    fun hideEditRemarkDialog() {
        _uiState.value = _uiState.value.copy(editRemarkTarget = null)
    }

    fun updateRemark(rootId: String, member: ProjectMember, remark: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(editRemarkTarget = null)
            val memberId = member.userId ?: return@launch
            spaceRepository.updateAccessRemark(rootId, memberId, remark)
                .onSuccess { load(rootId) }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.toUiText())
                }
        }
    }

    // ---- 移除成员（管理员） ----

    fun showRemoveConfirm(member: ProjectMember) {
        _uiState.value = _uiState.value.copy(removeTarget = member)
    }

    fun hideRemoveConfirm() {
        _uiState.value = _uiState.value.copy(removeTarget = null)
    }

    fun removeMember(rootId: String, memberId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(removeTarget = null)
            spaceRepository.removeAccess(rootId, memberId)
                .onSuccess { load(rootId) }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.toUiText())
                }
        }
    }

    // ---- 退出项目（本人） ----

    fun showLeaveConfirm() {
        _uiState.value = _uiState.value.copy(showLeaveConfirm = true)
    }

    fun hideLeaveConfirm() {
        _uiState.value = _uiState.value.copy(showLeaveConfirm = false)
    }

    fun leaveProject(rootId: String) {
        val currentUserId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showLeaveConfirm = false)
            spaceRepository.removeAccess(rootId, currentUserId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(hasLeft = true)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.toUiText())
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
