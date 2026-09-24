package cc.openxiot.matrix.data.repository

import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.api.RetrofitClient
import cc.openxiot.matrix.data.api.PlatformInfo
import cc.openxiot.matrix.data.api.OAuthToken

class AuthRepository {
    private val service get() = RetrofitClient.accountService

    /**
     * 用微信授权 code 换登录 token。
     * 服务端返回 JSON:{"success":true,"data":{"token":...,"name":...,"avatar":...,"platform":"wechat"}}
     */
    suspend fun exchangeWeixinCode(code: String): Result<OAuthToken> = runCatching {
        val response = service.oauthLogin("wechat", code)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: failWith(R.string.err_response_empty)
        } else {
            response.failWith(R.string.err_auth_login)
        }
    }

    suspend fun getGithubPlatform(): Result<PlatformInfo> = runCatching {
        val response = service.getGithubPlatform()
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data!!
        } else {
            response.failWith(R.string.err_auth_platform_info)
        }
    }

    suspend fun getPlatforms(): Result<List<PlatformInfo>> = runCatching {
        val response = service.getPlatforms()
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data!!
        } else {
            response.failWith(R.string.err_auth_platform_list)
        }
    }
}
