package cc.openxiot.wematrix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import cc.openxiot.wematrix.data.api.RetrofitClient
import cc.openxiot.wematrix.navigation.AppNavigation
import cc.openxiot.wematrix.navigation.Screen
import cc.openxiot.wematrix.ui.theme.WeMatrixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tokenManager = WeMatrixApp.instance.tokenManager

        // Init app state
        AppState.init(tokenManager)

        // Restore auth token and org ID for API calls
        tokenManager.token?.let { RetrofitClient.setToken(it) }
        tokenManager.currentOrgId?.let { RetrofitClient.setOrgId(it) }

        // Handle OAuth callback deep link from cold start
        intent?.data?.let { uri ->
            if (uri.scheme == "openxiot" && uri.host == "oauth") {
                handleOAuthCallback(uri.toString())
            }
        }

        setContent {
            WeMatrixTheme(darkTheme = AppState.isDarkMode) {
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
            val uri = Uri.parse(url)
            val token = uri.getQueryParameter("token")
            val name = uri.getQueryParameter("name")
            val avatar = uri.getQueryParameter("avatar")
            val platform = uri.getQueryParameter("platform")

            val tokenManager = WeMatrixApp.instance.tokenManager
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
