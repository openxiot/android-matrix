package cc.openxiot.wematrix.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.openxiot.wematrix.AppState
import cc.openxiot.wematrix.WeMatrixApp
import cc.openxiot.wematrix.ui.components.AvatarImage
import cc.openxiot.wematrix.ui.main.PageTitle

@Composable
fun ProfileScreen(
    currentOrgName: String?,
    currentProjectName: String?,
    organizationEnabled: Boolean = true,
    onNavigateToOrgPicker: () -> Unit = {},
    onNavigateToProjectPicker: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToModbus: () -> Unit = {}
) {
    val tokenManager = WeMatrixApp.instance.tokenManager

    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle(title = "我")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Profile header (clickable → account detail)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable(onClick = onNavigateToAccount),
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
                        Column(modifier = Modifier.weight(1f)) {
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
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Current organization（组织未启用时不显示，对齐 webapp 隐藏"组织"菜单）
            if (organizationEnabled) {
                item {
                    SectionHeader("当前组织")
                    SettingsCard(
                        title = currentOrgName ?: "未选择组织",
                        subtitle = "选择组织后方可选择项目",
                        icon = Icons.Default.Group,
                        onClick = onNavigateToOrgPicker
                    )
                }
            }

            // Current project（始终显示；组织禁用时也可直接进入项目选择）
            item {
                SectionHeader("当前项目")
                SettingsCard(
                    title = currentProjectName ?: "未选择项目",
                    subtitle = when {
                        !organizationEnabled -> "请选择项目"
                        currentOrgName != null -> "点击切换项目"
                        else -> "请先选择组织"
                    },
                    icon = Icons.Default.Business,
                    onClick = {
                        // 组织禁用或已选组织时均可进入；仅"组织启用但未选组织"时提示先选组织
                        if (!organizationEnabled || currentOrgName != null) onNavigateToProjectPicker()
                    }
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
                    showChevron = false,
                    onClick = { AppState.toggleDarkMode(tokenManager) }
                )
            }

            // 其他：设备点表入口对齐 web 的侧边栏 —— 只看「已启用组织」这一个开关
            // （不看管理员、不看 DTU）。关掉时这张卡不渲染，「其他」标题仍归下面的「关于」
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("其他")
                if (organizationEnabled) {
                    SettingsCard(
                        title = "设备点表",
                        subtitle = "Modbus 点表配置（只读）",
                        icon = Icons.Default.TableChart,
                        onClick = onNavigateToModbus
                    )
                }
                SettingsCard(
                    title = "关于",
                    subtitle = "应用信息与版本",
                    icon = Icons.Default.Info,
                    onClick = onNavigateToAbout
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
    icon: ImageVector,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    showChevron: Boolean = true,
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
            } else if (showChevron) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

