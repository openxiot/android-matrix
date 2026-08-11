package cc.openxiot.wematrix.data.repository

import cc.openxiot.wematrix.data.api.RetrofitClient
import cc.openxiot.wematrix.data.api.PlatformInfo
import cc.openxiot.wematrix.data.api.OAuthToken

class AuthRepository {
    private val service get() = RetrofitClient.accountService

    /**
     * 用微信授权 code 换登录 token。
     * 服务端返回 JSON:{"success":true,"data":{"token":...,"name":...,"avatar":...,"platform":"wechat"}}
     */
    suspend fun exchangeWeixinCode(code: String): Result<OAuthToken> = runCatching {
        val response = service.oauthLogin("wechat", code)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: throw Exception("返回数据为空")
        } else {
            throw Exception(response.body()?.message ?: "登录失败")
        }
    }

    suspend fun getGithubPlatform(): Result<PlatformInfo> = runCatching {
        val response = service.getGithubPlatform()
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data!!
        } else {
            throw Exception(response.body()?.message ?: "获取平台信息失败")
        }
    }

    suspend fun getPlatforms(): Result<List<PlatformInfo>> = runCatching {
        val response = service.getPlatforms()
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data!!
        } else {
            throw Exception(response.body()?.message ?: "获取平台列表失败")
        }
    }
}
