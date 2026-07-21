package cc.openxiot.android.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import cc.openxiot.android.OpenXiotApp
import cc.openxiot.android.data.api.Organization
import cc.openxiot.android.ui.login.LoginScreen
import cc.openxiot.android.ui.organization.OrganizationListScreen
import cc.openxiot.android.ui.project.ProjectListScreen
import cc.openxiot.android.ui.project.SpaceTreeScreen

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Organizations : Screen("organizations")
    data object Projects : Screen("projects/{orgId}") {
        fun createRoute(orgId: String) = "projects/$orgId"
    }
    data object SpaceTree : Screen("space_tree/{rootId}") {
        fun createRoute(rootId: String) = "space_tree/$rootId"
    }
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
                    navController.navigate(Screen.Organizations.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Organizations.route) {
            OrganizationListScreen(
                onOrgSelected = { org ->
                    org.id?.let { orgId ->
                        navController.navigate(Screen.Projects.createRoute(orgId))
                    }
                },
                onLogout = {
                    tokenManager.clear()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Projects.route,
            arguments = listOf(navArgument("orgId") { type = NavType.StringType })
        ) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId")
            ProjectListScreen(
                tenantId = orgId,
                onProjectSelected = { space ->
                    space.id?.let { rootId ->
                        navController.navigate(Screen.SpaceTree.createRoute(rootId))
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.SpaceTree.route,
            arguments = listOf(navArgument("rootId") { type = NavType.StringType })
        ) { backStackEntry ->
            val rootId = backStackEntry.arguments?.getString("rootId") ?: return@composable
            SpaceTreeScreen(
                rootId = rootId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
