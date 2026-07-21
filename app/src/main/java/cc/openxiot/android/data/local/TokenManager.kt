package cc.openxiot.android.data.local

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("openxiot_prefs", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var avatar: String?
        get() = prefs.getString(KEY_AVATAR, null)
        set(value) = prefs.edit().putString(KEY_AVATAR, value).apply()

    var platform: String?
        get() = prefs.getString(KEY_PLATFORM, null)
        set(value) = prefs.edit().putString(KEY_PLATFORM, value).apply()

    var currentOrgId: String?
        get() = prefs.getString(KEY_ORG_ID, null)
        set(value) = prefs.edit().putString(KEY_ORG_ID, value).apply()

    var currentOrgName: String?
        get() = prefs.getString(KEY_ORG_NAME, null)
        set(value) = prefs.edit().putString(KEY_ORG_NAME, value).apply()

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    val isLoggedIn: Boolean get() = token != null

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USERNAME)
            .remove(KEY_AVATAR)
            .remove(KEY_PLATFORM)
            .remove(KEY_ORG_ID)
            .remove(KEY_ORG_NAME)
            .apply()
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USERNAME = "username"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_PLATFORM = "platform"
        private const val KEY_ORG_ID = "current_org_id"
        private const val KEY_ORG_NAME = "current_org_name"
        private const val KEY_DARK_MODE = "dark_mode"
    }
}
