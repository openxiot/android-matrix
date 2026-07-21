package cc.openxiot.android.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import cc.openxiot.android.OpenXiotApp
import cc.openxiot.android.data.api.RetrofitClient
import cc.openxiot.android.ui.login.LoginScreen
import cc.openxiot.android.ui.main.MainScreen
import cc.openxiot.android.ui.organization.OrganizationListScreen
import cc.openxiot.android.ui.organization.OrganizationPickerScreen
import cc.openxiot.android.ui.project.ProjectManageScreen
import cc.openxiot.android.ui.project.ProjectPickerScreen

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Main : Screen("main")
    data object OrgPicker : Screen("org_picker")
    data object OrgManage : Screen("org_manage")
    data object ProjectPicker : Screen("project_picker")
    data object ProjectManage : Screen("project_manage")
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
                currentOrgName = tokenManager.currentOrgName,
                currentProjectName = tokenManager.currentRootSpaceName,
                onLogout = {
                    tokenManager.clear()
                    RetrofitClient.setToken(null)
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToOrgPicker = {
                    navController.navigate(Screen.OrgPicker.route)
                },
                onNavigateToProjectPicker = {
                    navController.navigate(Screen.ProjectPicker.route)
                }
            )
        }

        composable(Screen.OrgPicker.route) {
            OrganizationPickerScreen(
                onBack = { navController.popBackStack() },
                onManage = { navController.navigate(Screen.OrgManage.route) }
            )
        }

        composable(Screen.OrgManage.route) {
            OrganizationListScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ProjectPicker.route) {
            ProjectPickerScreen(
                onBack = { navController.popBackStack() },
                onManage = { navController.navigate(Screen.ProjectManage.route) }
            )
        }

        composable(Screen.ProjectManage.route) {
            ProjectManageScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
