package cc.openxiot.android.data.repository

import cc.openxiot.android.data.api.RetrofitClient
import cc.openxiot.android.data.api.PlatformInfo

class AuthRepository {
    private val service get() = RetrofitClient.accountService

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
