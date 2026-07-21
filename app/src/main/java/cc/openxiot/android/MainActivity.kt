package cc.openxiot.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.compose.rememberNavController
import cc.openxiot.android.data.api.RetrofitClient
import cc.openxiot.android.navigation.AppNavigation
import cc.openxiot.android.navigation.Screen
import cc.openxiot.android.ui.theme.OpenXiotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tokenManager = OpenXiotApp.instance.tokenManager

        // Init app state
        AppState.init(tokenManager)

        // Restore auth token for API calls
        tokenManager.token?.let { RetrofitClient.setToken(it) }

        // Handle OAuth callback deep link from cold start
        intent?.data?.let { uri ->
            if (uri.scheme == "openxiot" && uri.host == "oauth") {
                handleOAuthCallback(uri.toString())
            }
        }

        setContent {
            OpenXiotTheme(darkTheme = AppState.isDarkMode) {
                val navController = rememberNavController()
                val startDestination = if (AppState.isLoggedIn) {
                    Screen.Main.route
                } else {
                    Screen.Login.route
                }

                AppNavigation(
                    navController = navController,
                    startDestination = startDestination
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        intent.data?.let { uri ->
            if (uri.scheme == "openxiot" && uri.host == "oauth") {
                handleOAuthCallback(uri.toString())
            }
        }
    }

    private fun handleOAuthCallback(url: String) {
        try {
            val uri = android.net.Uri.parse(url)
            val token = uri.getQueryParameter("token")
            val name = uri.getQueryParameter("name")
            val avatar = uri.getQueryParameter("avatar")
            val platform = uri.getQueryParameter("platform")

            val tokenManager = OpenXiotApp.instance.tokenManager
            if (token != null) {
                tokenManager.token = token
                tokenManager.username = name
                tokenManager.avatar = avatar
                tokenManager.platform = platform
                RetrofitClient.setToken(token)
                AppState.setLoggedIn(tokenManager)
            }
        } catch (_: Exception) { }
    }
}
