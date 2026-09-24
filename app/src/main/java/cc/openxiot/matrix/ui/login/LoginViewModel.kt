package cc.openxiot.matrix.ui.login

import androidx.lifecycle.ViewModel
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import cc.openxiot.matrix.AppState
import cc.openxiot.matrix.R
import cc.openxiot.matrix.MatrixApp
import cc.openxiot.matrix.data.api.RetrofitClient
import cc.openxiot.matrix.data.repository.AuthRepository
import cc.openxiot.matrix.ui.core.UiText
import cc.openxiot.matrix.ui.core.toUiText
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.modelmsg.SendAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: UiText? = null
)

class LoginViewModel : ViewModel() {
    private val repository = AuthRepository()
    private val tokenManager = MatrixApp.instance.tokenManager

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

    /**
     * 处理微信授权回调结果(WXEntryActivity 经 Weixin.authResult 转发过来)。
     */
    fun handleWeixinResult(resp: SendAuth.Resp) {
        when (resp.errCode) {
            BaseResp.ErrCode.ERR_OK -> {
                val code = resp.code
                if (code.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(error = UiText.Res(R.string.login_err_no_code))
                } else {
                    exchangeWeixinCode(code)
                }
            }
            BaseResp.ErrCode.ERR_USER_CANCEL -> {
                _uiState.value = _uiState.value.copy(error = UiText.Res(R.string.login_err_cancelled))
            }
            else -> {
                // 微信 SDK 自己的 errStr 是英文技术串，有就原样上屏（对齐服务端 message 的口径）；
                // 没有才用我们的资源，把错误码嵌进去。
                val detail = resp.errStr
                _uiState.value = _uiState.value.copy(
                    error = detail?.let { UiText.Raw(it) }
                        ?: UiText.Res(R.string.login_err_wechat, listOf(resp.errCode))
                )
            }
        }
        Weixin.clearAuthResult()
    }

    /**
     * 第二步:用微信授权 code 换登录 token。
     */
    private fun exchangeWeixinCode(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.exchangeWeixinCode(code)
                .onSuccess { oauthToken ->
                    val token = oauthToken.token
                    if (token.isNullOrEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = UiText.Res(R.string.login_err_no_token)
                        )
                        return@launch
                    }
                    tokenManager.token = token
                    tokenManager.username = oauthToken.name
                    tokenManager.avatar = oauthToken.avatar
                    tokenManager.platform = "weixin"
                    tokenManager.developerId = tokenManager.extractDeveloperIdFromToken()
                    RetrofitClient.setToken(token)
                    AppState.setLoggedIn(tokenManager)
                    _uiState.value = LoginUiState(isLoggedIn = true)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.toUiText(R.string.err_auth_login)
                    )
                }
        }
    }

    fun useTestToken() {
        val testToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJodHRwczovL2V4YW1wbGUuY29tL2lzc3VlciIsInVwbiI6IjZhNGRhZmU1YTE3Nzg2ZGJlMDEyOTlhYyIsInVzZXJuYW1lIjoiZ2tjaXR5IiwiZ3JvdXBzIjpbImRldmVsb3BlciJdLCJiaXJ0aGRhdGUiOiJNb24gQXVnIDAzIDA4OjM1OjExIEdNVCAyMDI2IiwiZXhwIjoxNzg2MzUwOTExLCJpYXQiOjE3ODU3NDYxMTEsImp0aSI6IjJjYmJiMDdkLTAzMjItNGY5NC1hYzIwLThlZWQ2MDU0OWU5ZiJ9.GnOnHpocd9mwSm7RWlw-8LGzmthuE0UVlAsA1QXeo32JldXlBRcV-tkDZZjzQqBlpnKjz9Z_JjCThAkL5tBlL52Ez6z7S5B1YrHNfFH5eO8YiDXLiYMmVHYRUaKQTR2-GpgfSBlLue6e4L0JraLXJUyl0BsEYL86sJrUl3so0kZenx28GmBDKc1EsGbbtPhFZaXZ2g0sSxZvdEd2KEf4MCLuGcMSOkPVo1LR9vGLhVd2023bsV7Szps7ZPU--A9vzX4uryJHN2sI7ipTuuwPGAb_NW1Zj4yv03fTJX4-I-Fg_Zv59k0RTfE86qpbj1Tqm8hgMrdZSmHzkUmcvphk6g"
        tokenManager.token = testToken
        tokenManager.username = "gkcity"
        RetrofitClient.setToken(testToken)
        AppState.setLoggedIn(tokenManager)
        _uiState.value = LoginUiState(isLoggedIn = true)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun showError(@StringRes resId: Int) {
        _uiState.value = _uiState.value.copy(error = UiText.Res(resId))
    }
}
