package cc.openxiot.wematrix.data.repository

import cc.openxiot.wematrix.data.api.RetrofitClient
import cc.openxiot.wematrix.data.api.UserSettings

class UserSettingsRepository {
    private val service get() = RetrofitClient.accountService

    suspend fun getSettings(): Result<UserSettings> = runCatching {
        val response = service.getSettings()
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()?.data ?: UserSettings()
        } else {
            throw Exception(response.body()?.message ?: "获取用户设置失败")
        }
    }

    suspend fun updateSettings(organizationEnabled: Boolean): Result<UserSettings> = runCatching {
        val response = service.updateSettings(mapOf("organizationEnabled" to organizationEnabled))
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()?.data ?: UserSettings(organizationEnabled = organizationEnabled)
        } else {
            throw Exception(response.body()?.message ?: "更新用户设置失败")
        }
    }
}
