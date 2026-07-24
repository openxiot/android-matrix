package cc.openxiot.wematrix.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import cc.openxiot.wematrix.WeMatrixApp
import cc.openxiot.wematrix.data.api.RetrofitClient
import cc.openxiot.wematrix.ui.login.LoginScreen
import cc.openxiot.wematrix.ui.main.MainScreen
import cc.openxiot.wematrix.ui.organization.OrganizationDetailScreen
import cc.openxiot.wematrix.ui.organization.OrganizationPickerScreen
import cc.openxiot.wematrix.ui.profile.AboutScreen
import cc.openxiot.wematrix.ui.profile.AccountScreen
import cc.openxiot.wematrix.ui.project.ProjectPickerScreen
import cc.openxiot.wematrix.ui.device.DeviceDetailScreen
import cc.openxiot.wematrix.ui.device.DeviceOperationScreen
import cc.openxiot.wematrix.ui.products.ProductDetailScreen
import cc.openxiot.wematrix.ui.project.SpaceTreeScreen
import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Main : Screen("main")
    data object OrgPicker : Screen("org_picker")
    data object ProjectPicker : Screen("project_picker")
    data object ProjectEdit : Screen("project_edit/{rootId}")
    data object OrgDetail : Screen("org_detail/{orgId}")
    data object DeviceDetail : Screen("device_detail/{did}")
    data object DeviceOperation : Screen("device_operation/{did}?type={type}&spaceId={spaceId}")
    data object ProductDetail : Screen("product_detail/{productId}")
    data object Account : Screen("account")
    data object About : Screen("about")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String
) {
    val tokenManager = WeMatrixApp.instance.tokenManager

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
                onNavigateToOrgPicker = {
                    navController.navigate(Screen.OrgPicker.route) {
                        popUpTo(Screen.Main.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onNavigateToProjectPicker = {
                    navController.navigate(Screen.ProjectPicker.route) {
                        popUpTo(Screen.Main.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onNavigateToAccount = {
                    navController.navigate(Screen.Account.route) {
                        popUpTo(Screen.Main.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onNavigateToAbout = {
                    navController.navigate(Screen.About.route) { launchSingleTop = true }
                },
                onNavigateToDeviceDetail = { did ->
                    navController.navigate("device_detail/$did") { launchSingleTop = true }
                },
                onNavigateToDeviceOperation = { did, type, spaceId ->
                    navController.navigate(
                        "device_operation/$did?type=${
                            URLEncoder.encode(
                                type,
                                "UTF-8"
                            )
                        }&spaceId=$spaceId"
                    ) { launchSingleTop = true }
                },
                onNavigateToProductDetail = { productId ->
                    navController.navigate("product_detail/$productId") { launchSingleTop = true }
                }
            )
        }

        composable(Screen.Account.route) {
            AccountScreen(
                onBack = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Main.route) {
                            inclusive = false
                        }; launchSingleTop = true
                    }
                },
                onLogout = {
                    tokenManager.clear()
                    RetrofitClient.setToken(null)
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.OrgPicker.route) {
            OrganizationPickerScreen(
                onBack = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Main.route) {
                            inclusive = false
                        }; launchSingleTop = true
                    }
                },
                onNavigateToDetail = { orgId ->
                    navController.navigate("org_detail/$orgId") { launchSingleTop = true }
                }
            )
        }

        composable(Screen.ProjectPicker.route) {
            ProjectPickerScreen(
                onBack = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Main.route) {
                            inclusive = false
                        }; launchSingleTop = true
                    }
                },
                onEditProject = { rootId ->
                    navController.navigate("project_edit/$rootId") { launchSingleTop = true }
                }
            )
        }

        composable(Screen.OrgDetail.route) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: return@composable
            OrganizationDetailScreen(
                orgId = orgId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ProjectEdit.route) { backStackEntry ->
            val rootId = backStackEntry.arguments?.getString("rootId") ?: return@composable
            SpaceTreeScreen(
                rootId = rootId,
                onBack = {
                    navController.navigate(Screen.ProjectPicker.route) {
                        launchSingleTop = true
                    }
                },
                onDeviceDetail = { did ->
                    navController.navigate("device_detail/$did") { launchSingleTop = true }
                },
                onDeviceOperation = { did, type, spaceId ->
                    navController.navigate(
                        "device_operation/$did?type=${
                            URLEncoder.encode(
                                type,
                                "UTF-8"
                            )
                        }&spaceId=$spaceId"
                    ) { launchSingleTop = true }
                }
            )
        }

        composable(Screen.DeviceDetail.route) { backStackEntry ->
            val did = backStackEntry.arguments?.getString("did") ?: return@composable
            DeviceDetailScreen(
                did = did,
                onBack = { navController.popBackStack() },
                onMoveDevice = { d ->
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.DeviceOperation.route) { backStackEntry ->
            val did = backStackEntry.arguments?.getString("did") ?: return@composable
            val type = backStackEntry.arguments?.getString("type") ?: return@composable
            val spaceId = backStackEntry.arguments?.getString("spaceId") ?: return@composable
            DeviceOperationScreen(
                deviceType = type,
                deviceDid = did,
                spaceId = spaceId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ProductDetail.route) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId") ?: return@composable
            ProductDetailScreen(
                productId = productId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
