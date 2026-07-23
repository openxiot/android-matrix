package cc.openxiot.wematrix.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class TokenManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("openxiot_prefs", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit { putString(KEY_TOKEN, value) }

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.edit { putString(KEY_USERNAME, value) }

    var avatar: String?
        get() = prefs.getString(KEY_AVATAR, null)
        set(value) = prefs.edit { putString(KEY_AVATAR, value) }

    var platform: String?
        get() = prefs.getString(KEY_PLATFORM, null)
        set(value) = prefs.edit { putString(KEY_PLATFORM, value) }

    var currentOrgId: String?
        get() = prefs.getString(KEY_ORG_ID, null)
        set(value) = prefs.edit { putString(KEY_ORG_ID, value) }

    var currentOrgName: String?
        get() = prefs.getString(KEY_ORG_NAME, null)
        set(value) = prefs.edit { putString(KEY_ORG_NAME, value) }

    var currentRootSpaceId: String?
        get() = prefs.getString(KEY_ROOT_SPACE_ID, null)
        set(value) = prefs.edit { putString(KEY_ROOT_SPACE_ID, value) }

    var currentRootSpaceName: String?
        get() = prefs.getString(KEY_ROOT_SPACE_NAME, null)
        set(value) = prefs.edit { putString(KEY_ROOT_SPACE_NAME, value) }

    var developerId: String?
        get() = prefs.getString(KEY_DEVELOPER_ID, null)
        set(value) = prefs.edit { putString(KEY_DEVELOPER_ID, value) }

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) = prefs.edit { putBoolean(KEY_DARK_MODE, value) }

    val isLoggedIn: Boolean get() = token != null

    fun clear() {
        prefs.edit { clear() }
    }

    fun clearSession() {
        prefs.edit {
            remove(KEY_TOKEN)
                .remove(KEY_USERNAME)
                .remove(KEY_AVATAR)
                .remove(KEY_PLATFORM)
                .remove(KEY_ORG_ID)
                .remove(KEY_ORG_NAME)
        }
    }

    fun extractDeveloperIdFromToken(): String? {
        val t = token ?: return null
        return try {
            val parts = t.split(".")
            if (parts.size < 2) return null
            val payload = try {
                String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING))
            } catch (_: Exception) {
                String(android.util.Base64.decode(parts[1], android.util.Base64.DEFAULT))
            }
            val json = org.json.JSONObject(payload)
            // Try common JWT claims for user identifier
            json.optString("sub").takeIf { it.isNotEmpty() }
                ?: json.optString("preferred_username").takeIf { it.isNotEmpty() }
                ?: json.optString("clientId").takeIf { it.isNotEmpty() }
        } catch (_: Exception) { null }
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USERNAME = "username"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_PLATFORM = "platform"
        private const val KEY_ORG_ID = "current_org_id"
        private const val KEY_ORG_NAME = "current_org_name"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_DEVELOPER_ID = "developer_id"
        private const val KEY_ROOT_SPACE_ID = "current_root_space_id"
        private const val KEY_ROOT_SPACE_NAME = "current_root_space_name"
    }
}
