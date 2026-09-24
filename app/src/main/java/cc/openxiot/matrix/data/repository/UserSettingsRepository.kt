package cc.openxiot.matrix.data.repository

import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.api.RetrofitClient
import cc.openxiot.matrix.data.api.UserSettings

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
