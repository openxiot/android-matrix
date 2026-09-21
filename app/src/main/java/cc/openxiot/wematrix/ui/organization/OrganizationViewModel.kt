package cc.openxiot.wematrix.ui.organization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.wematrix.WeMatrixApp
import cc.openxiot.wematrix.data.api.Organization
import cc.openxiot.wematrix.data.api.RetrofitClient
import cc.openxiot.wematrix.data.repository.OrganizationRepository
import cc.openxiot.wematrix.ui.core.UiText
import cc.openxiot.wematrix.ui.core.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OrgListUiState(
    val isLoading: Boolean = true,
    val organizations: List<Organization> = emptyList(),
    val currentOrgId: String? = null,
    val currentOrgName: String? = null,
    val error: UiText? = null,
    val showCreateDialog: Boolean = false
)

class OrganizationViewModel : ViewModel() {
    private val repository = OrganizationRepository()
    private val tokenManager = WeMatrixApp.instance.tokenManager

    private val _uiState = MutableStateFlow(OrgListUiState(
        currentOrgId = tokenManager.currentOrgId,
        currentOrgName = tokenManager.currentOrgName
    ))
    val uiState: StateFlow<OrgListUiState> = _uiState.asStateFlow()

    init {
        loadOrganizations()
    }

    fun loadOrganizations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getMyOrganizations()
                .onSuccess { orgs ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        organizations = orgs
                    )
                    // Auto-select first org if none selected
                    if (_uiState.value.currentOrgId == null && orgs.isNotEmpty()) {
                        selectOrganization(orgs[0])
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.toUiText()
                    )
                }
        }
    }

    fun selectOrganization(org: Organization) {
        val orgId = org.id ?: return
        val orgName = org.name ?: orgId
        tokenManager.currentOrgId = orgId
        tokenManager.currentOrgName = orgName
        // Clear current project since it belongs to the previous org
        tokenManager.currentRootSpaceId = null
        tokenManager.currentRootSpaceName = null
        RetrofitClient.setOrgId(orgId)
        _uiState.value = _uiState.value.copy(
            currentOrgId = orgId,
            currentOrgName = orgName
        )
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
    }

    fun hideCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
    }

    fun createOrganization(id: String, name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, showCreateDialog = false)
            repository.createOrganization(id, name)
                .onSuccess {
                    loadOrganizations()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.toUiText()
                    )
                }
        }
    }

    fun renameOrganization(id: String, newName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)
            repository.updateOrganization(id, newName)
                .onSuccess {
                    if (_uiState.value.currentOrgId == id) {
                        tokenManager.currentOrgName = newName
                        _uiState.value = _uiState.value.copy(currentOrgName = newName)
                    }
                    loadOrganizations()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.toUiText())
                }
        }
    }

    fun deleteOrganization(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.deleteOrganization(id)
                .onSuccess {
                    if (_uiState.value.currentOrgId == id) {
                        tokenManager.currentOrgId = null
                        tokenManager.currentOrgName = null
                    }
                    loadOrganizations()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.toUiText()
                    )
                }
        }
    }

    fun logout() {
        tokenManager.clear()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
