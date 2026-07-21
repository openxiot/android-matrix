package cc.openxiot.android.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import cc.openxiot.android.OpenXiotApp
import cc.openxiot.android.data.api.RetrofitClient
import cc.openxiot.android.ui.login.LoginScreen
import cc.openxiot.android.ui.main.MainScreen

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Main : Screen("main")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String
) {
    val tokenManager = OpenXiotApp.instance.tokenManager

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            MainScreen(
                onLogout = {
                    tokenManager.clear()
                    RetrofitClient.setToken(null)
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
