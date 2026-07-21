package cc.openxiot.android.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.android.ui.devices.DeviceListScreen
import cc.openxiot.android.ui.organization.OrganizationViewModel
import cc.openxiot.android.ui.products.ProductListScreen
import cc.openxiot.android.ui.profile.ProfileScreen
import cc.openxiot.android.ui.project.ProjectViewModel
import cc.openxiot.android.ui.project.SpaceTreeContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    mainViewModel: MainViewModel = viewModel(),
    projectViewModel: ProjectViewModel = viewModel(),
    orgViewModel: OrganizationViewModel = viewModel()
) {
    val tabs = BottomTab.entries
    var selectedTab by remember { mutableStateOf(BottomTab.Projects) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                tabs.forEach { tab ->
                    val selected = selectedTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                tab.label,
                                color = if (selected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                BottomTab.Projects -> {
                    val rootId = mainViewModel.currentRootSpaceId
                    if (rootId != null) {
                        SpaceTreeContent(
                            rootId = rootId,
                            viewModel = projectViewModel
                        )
                    } else {
                        EmptyProjectHint(onLogout = onLogout)
                    }
                }
                BottomTab.Devices -> DeviceListScreen(
                    rootId = mainViewModel.currentRootSpaceId
                )
                BottomTab.Products -> ProductListScreen()
                BottomTab.Profile -> ProfileScreen(
                    orgViewModel = orgViewModel,
                    onLogout = onLogout
                )
            }
        }
    }
}

@Composable
private fun EmptyProjectHint(onLogout: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "请先在「我」的页面选择项目和组织",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onLogout) {
                Text("退出登录")
            }
        }
    }
}
