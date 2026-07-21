package cc.openxiot.android.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.android.ui.devices.DeviceListScreen
import cc.openxiot.android.ui.products.ProductListScreen
import cc.openxiot.android.ui.profile.ProfileScreen
import cc.openxiot.android.ui.project.ProjectViewModel
import cc.openxiot.android.ui.project.SpaceTreeContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    currentOrgName: String?,
    currentProjectName: String?,
    onLogout: () -> Unit,
    onNavigateToOrgPicker: () -> Unit = {},
    onNavigateToProjectPicker: () -> Unit = {},
    mainViewModel: MainViewModel = viewModel(),
    projectViewModel: ProjectViewModel = viewModel()
) {
    val tabs = BottomTab.entries

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(56.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                tabs.forEach { tab ->
                    val selected = mainViewModel.currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { mainViewModel.selectTab(tab) },
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(22.dp),
                                tint = if (selected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelSmall,
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
            when (mainViewModel.currentTab) {
                BottomTab.Projects -> {
                    val rootId = mainViewModel.currentRootSpaceId
                    if (rootId != null) {
                        Column {
                            PageTitle(title = "项目")
                            SpaceTreeContent(
                                rootId = rootId,
                                viewModel = projectViewModel
                            )
                        }
                    } else {
                        EmptyProjectHint(onLogout = onLogout)
                    }
                }
                BottomTab.Devices -> DeviceListScreen(
                    rootId = mainViewModel.currentRootSpaceId
                )
                BottomTab.Products -> ProductListScreen()
                BottomTab.Profile -> ProfileScreen(
                    currentOrgName = currentOrgName,
                    currentProjectName = currentProjectName,
                    onLogout = onLogout,
                    onNavigateToOrgPicker = onNavigateToOrgPicker,
                    onNavigateToProjectPicker = onNavigateToProjectPicker
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

@Composable
fun PageTitle(title: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}
