package cc.openxiot.wematrix.data.repository

import cc.openxiot.wematrix.R
import cc.openxiot.wematrix.data.api.RetrofitClient
import cc.openxiot.wematrix.data.api.UserSettings

class UserSettingsRepository {
    private val service get() = RetrofitClient.accountService

    suspend fun getSettings(): Result<UserSettings> = runCatching {
        val response = service.getSettings()
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()?.data ?: UserSettings()
        } else {
            response.failWith(R.string.err_settings_get)
        }
    }

    suspend fun updateSettings(organizationEnabled: Boolean): Result<UserSettings> = runCatching {
        val response = service.updateSettings(mapOf("organizationEnabled" to organizationEnabled))
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()?.data ?: UserSettings(organizationEnabled = organizationEnabled)
        } else {
            response.failWith(R.string.err_settings_update)
        }
    }
}
