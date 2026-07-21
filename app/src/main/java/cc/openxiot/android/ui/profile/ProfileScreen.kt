package cc.openxiot.android.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.android.AppState
import cc.openxiot.android.OpenXiotApp
import cc.openxiot.android.ui.components.AvatarImage
import cc.openxiot.android.ui.main.PageTitle
import cc.openxiot.android.ui.organization.OrganizationViewModel
import cc.openxiot.android.ui.project.ProjectViewModel

@Composable
fun ProfileScreen(
    orgViewModel: OrganizationViewModel,
    onLogout: () -> Unit,
    onNavigateToOrgPicker: () -> Unit = {},
    onNavigateToProjectPicker: () -> Unit = {},
    projectViewModel: ProjectViewModel = viewModel()
) {
    val tokenManager = OpenXiotApp.instance.tokenManager
    val orgState by orgViewModel.uiState.collectAsState()
    val projectState by projectViewModel.projectState.collectAsState()

    // Load orgs when screen appears
    LaunchedEffect(Unit) {
        if (orgState.organizations.isEmpty()) {
            orgViewModel.loadOrganizations()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle(title = "我")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Profile header
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarImage(
                            url = tokenManager.avatar,
                            name = tokenManager.username,
                            size = 56.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = tokenManager.username ?: "未登录",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = tokenManager.platform ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Current organization
            item {
                SectionHeader("当前组织")
                SettingsCard(
                    title = orgState.currentOrgName ?: "未选择组织",
                    subtitle = "点击管理组织",
                    icon = Icons.Default.Group,
                    onClick = onNavigateToOrgPicker
                )
            }

            // Current project
            item {
                SectionHeader("当前项目")
                SettingsCard(
                    title = projectState.currentRootName ?: "未选择项目",
                    subtitle = "点击切换项目",
                    icon = Icons.Default.Business,
                    onClick = onNavigateToProjectPicker
                )
            }

            // Settings
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("设置")
                SettingsCard(
                    title = if (AppState.isDarkMode) "深色模式" else "浅色模式",
                    subtitle = "点击切换主题",
                    icon = if (AppState.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                    trailing = {
                        Switch(
                            checked = AppState.isDarkMode,
                            onCheckedChange = { AppState.toggleDarkMode(tokenManager) }
                        )
                    },
                    onClick = { AppState.toggleDarkMode(tokenManager) }
                )
                SettingsCard(
                    title = "退出登录",
                    subtitle = "清除登录状态",
                    icon = Icons.AutoMirrored.Filled.Logout,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = {
                        tokenManager.clear()
                        onLogout()
                    }
                )
            }
        }
    }

}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

