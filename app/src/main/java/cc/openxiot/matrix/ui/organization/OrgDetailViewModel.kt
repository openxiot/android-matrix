package cc.openxiot.matrix.ui.organization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.matrix.MatrixApp
import cc.openxiot.matrix.data.api.Member
import cc.openxiot.matrix.data.api.Organization
import cc.openxiot.matrix.data.repository.OrganizationRepository
import cc.openxiot.matrix.ui.core.UiText
import cc.openxiot.matrix.ui.core.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OrgDetailUiState(
    val isLoading: Boolean = true,
    val organization: Organization? = null,
    val members: List<Member> = emptyList(),
    val currentUserId: String? = null,
    val isCurrentUserAdmin: Boolean = false,
    val error: UiText? = null,
    val showAddDialog: Boolean = false,
    val changeRoleTarget: Member? = null,
    val removeTarget: Member? = null
)

class OrgDetailViewModel : ViewModel() {
    private val repository = OrganizationRepository()
    private val tokenManager = MatrixApp.instance.tokenManager

    private val _uiState = MutableStateFlow(OrgDetailUiState(
        currentUserId = tokenManager.developerId
    ))
    val uiState: StateFlow<OrgDetailUiState> = _uiState.asStateFlow()

    fun loadOrganization(orgId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            // Try to extract developerId from token if not set
            if (_uiState.value.currentUserId == null) {
                tokenManager.developerId = tokenManager.extractDeveloperIdFromToken()
                _uiState.value = _uiState.value.copy(currentUserId = tokenManager.developerId)
            }
            repository.getOrganization(orgId)
                .onSuccess { org ->
                    val members = org.members ?: emptyList()
                    val currentUserId = _uiState.value.currentUserId
                    val currentUserName = tokenManager.username
                    // Try matching by developerId, then by name
                    val currentMember = members.find { it.developerId == currentUserId }
                        ?: members.firstOrNull { it.name == currentUserName }
                    val isAdmin = currentMember?.role == "admin"
                    // Update currentUserId from matched member for subsequent operations
                    if (currentMember != null && currentUserId == null) {
                        _uiState.value = _uiState.value.copy(currentUserId = currentMember.developerId)
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        organization = org,
                        members = members,
                        isCurrentUserAdmin = isAdmin,
                        currentUserId = currentMember?.developerId ?: currentUserId
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

    fun showAddMemberDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun hideAddMemberDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun addMember(orgId: String, developerId: String, name: String, role: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showAddDialog = false)
            repository.addMember(orgId, Member(developerId = developerId, name = name, role = role))
                .onSuccess { loadOrganization(orgId) }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.toUiText())
                }
        }
    }

    fun showChangeRoleDialog(member: Member) {
        _uiState.value = _uiState.value.copy(changeRoleTarget = member)
    }

    fun hideChangeRoleDialog() {
        _uiState.value = _uiState.value.copy(changeRoleTarget = null)
    }

    fun updateMemberRole(orgId: String, developerId: String, memberName: String, newRole: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(changeRoleTarget = null)
            repository.updateMember(orgId, Member(developerId = developerId, name = memberName, role = newRole))
                .onSuccess { loadOrganization(orgId) }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.toUiText())
                }
        }
    }

    fun showRemoveConfirm(member: Member) {
        _uiState.value = _uiState.value.copy(removeTarget = member)
    }

    fun hideRemoveConfirm() {
        _uiState.value = _uiState.value.copy(removeTarget = null)
    }

    fun removeMember(orgId: String, memberId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(removeTarget = null)
            repository.removeMember(orgId, memberId)
                .onSuccess { loadOrganization(orgId) }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.toUiText())
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
