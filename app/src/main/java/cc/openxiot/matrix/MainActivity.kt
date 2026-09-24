package cc.openxiot.matrix

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import cc.openxiot.matrix.data.api.RetrofitClient
import cc.openxiot.matrix.navigation.AppNavigation
import cc.openxiot.matrix.navigation.Screen
import cc.openxiot.matrix.ui.theme.MatrixTheme

class MainActivity : ComponentActivity() {
    /** 应用语言。⚠️ 每个带界面的 Activity 都要有这一句，漏了就永远跟系统语言走。 */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tokenManager = MatrixApp.instance.tokenManager

        // Init app state
        AppState.init(tokenManager)

        // Restore auth token and org ID for API calls
        tokenManager.token?.let { RetrofitClient.setToken(it) }
        tokenManager.currentOrgId?.let { RetrofitClient.setOrgId(it) }
        // Ensure developerId is extracted from existing token
        if (tokenManager.developerId == null) {
            tokenManager.developerId = tokenManager.extractDeveloperIdFromToken()
        }

        // Handle OAuth callback deep link from cold start。
        // savedInstanceState != null 时说明这是重建（旋转屏幕、或切语言触发的 recreate），
        // 而 recreate 会把原始 intent 一起带进来 —— 不拦的话回调会被重放一次。
        if (savedInstanceState == null) {
            intent?.data?.let { uri ->
                if (uri.scheme == "openxiot" && uri.host == "oauth") {
                    handleOAuthCallback(uri.toString())
                }
            }
        }

        setContent {
            MatrixTheme(darkTheme = AppState.isDarkMode) {
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

            val tokenManager = MatrixApp.instance.tokenManager
            if (token != null) {
                tokenManager.token = token
                tokenManager.username = name
                tokenManager.avatar = avatar
                tokenManager.platform = platform
                tokenManager.developerId = tokenManager.extractDeveloperIdFromToken()
                RetrofitClient.setToken(token)
                AppState.setLoggedIn(tokenManager)
            }
        } catch (_: Exception) { }
    }
}
