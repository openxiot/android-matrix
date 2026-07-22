package cc.openxiot.android.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
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
    onNavigateToOrgPicker: () -> Unit = {},
    onNavigateToProjectPicker: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {},
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
                            PageTitle(
                                title = currentProjectName ?: "项目",
                                onClick = onNavigateToProjectPicker,
                                actions = {
                                    IconButton(onClick = { /* TODO */ }) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "添加",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                            SpaceTreeContent(
                                rootId = rootId,
                                viewModel = projectViewModel
                            )
                        }
                    } else if (currentOrgName == null) {
                        EmptyHint(
                            message = "请先选择当前组织",
                            buttonText = "选择组织",
                            onClick = onNavigateToOrgPicker
                        )
                    } else {
                        EmptyHint(
                            message = "请先选择当前项目",
                            buttonText = "选择项目",
                            onClick = onNavigateToProjectPicker
                        )
                    }
                }
                BottomTab.Devices -> DeviceListScreen(
                    rootId = mainViewModel.currentRootSpaceId
                )
                BottomTab.Products -> ProductListScreen()
                BottomTab.Profile -> ProfileScreen(
                    currentOrgName = currentOrgName,
                    currentProjectName = currentProjectName,
                    onNavigateToOrgPicker = onNavigateToOrgPicker,
                    onNavigateToProjectPicker = onNavigateToProjectPicker,
                    onNavigateToAccount = onNavigateToAccount
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(
    message: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = onClick) {
                Text(buttonText)
            }
        }
    }
}

@Composable
fun PageTitle(
    title: String,
    onClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onClick != null) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable(onClick = onClick)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            actions()
        }
    }
}
