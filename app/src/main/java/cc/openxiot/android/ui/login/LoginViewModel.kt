package cc.openxiot.android.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.android.AppState
import cc.openxiot.android.OpenXiotApp
import cc.openxiot.android.data.api.RetrofitClient
import cc.openxiot.android.data.repository.AuthRepository
import cc.openxiot.android.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val githubUrl: String? = null,
    val isLoggedIn: Boolean = false,
    val error: String? = null
)

class LoginViewModel : ViewModel() {
    private val repository = AuthRepository()
    private val tokenManager = OpenXiotApp.instance.tokenManager

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        if (tokenManager.isLoggedIn) {
            RetrofitClient.setToken(tokenManager.token)
            _uiState.value = LoginUiState(isLoggedIn = true)
        }
    }

    fun checkLoginAfterOAuth() {
        if (tokenManager.isLoggedIn && !_uiState.value.isLoggedIn) {
            RetrofitClient.setToken(tokenManager.token)
            _uiState.value = LoginUiState(isLoggedIn = true)
        }
    }

    fun loadGithubUrl() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getGithubPlatform()
                .onSuccess { platform ->
                    val state = android.util.Base64.encodeToString(
                        Constants.OAUTH_CALLBACK.toByteArray(),
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                    )
                    val url = "${platform.authorizeUrl}?client_id=${platform.clientId}" +
                            "&redirect_uri=${platform.callbackUrl}" +
                            "&state=$state&scope=read:user,user:email"
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        githubUrl = url
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "加载失败"
                    )
                }
        }
    }

    fun useTestToken() {
        val testToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJodHRwczovL2V4YW1wbGUuY29tL2lzc3VlciIsInVwbiI6IjZhNGRhZmU1YTE3Nzg2ZGJlMDEyOTlhYyIsInVzZXJuYW1lIjoiZ2tjaXR5IiwiZ3JvdXBzIjpbImRldmVsb3BlciJdLCJiaXJ0aGRhdGUiOiJGcmkgSnVsIDE3IDAyOjE2OjAyIEdNVCAyMDI2IiwiZXhwIjoxNzg0ODU5MzYyLCJpYXQiOjE3ODQyNTQ1NjIsImp0aSI6ImRlM2NhMTRhLTkwMzUtNGQ3Zi05ZTJhLTM1MTg3YmIxMmYyNSJ9.Tw3RkfVCchzEDYQQs9pckQKsF6OoZZtXU6FbZYeNBc3McurOeKLKVQrOcH-usNEvJYgcbx-U1zoCREE9kdd0yylJmUQuopVX7gBCnCZU8-7dCZLQ6qYzhGLGOV-1GBIi3oE_PpiJxkBqYyW1diBesyo6aoAbgoVToX5hGJPjrHFnXDAyboZRX8wHsimSrAR98RyDwRTnnXMFhez0OPS4-y2FolN14dDB_0xN0vtzx1S4NGbbVq5F2f1pXIchanlUmHBb4SSDoeCp19QB7HnPZbx8U4qv-wVahvNg57R5EODsTLBZMKARUIfCYkAfdEhMbWBqvXjwpgetIbwf4KLNyw"
        tokenManager.token = testToken
        tokenManager.username = "gkcity"
        RetrofitClient.setToken(testToken)
        AppState.setLoggedIn(tokenManager)
        _uiState.value = LoginUiState(isLoggedIn = true)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
