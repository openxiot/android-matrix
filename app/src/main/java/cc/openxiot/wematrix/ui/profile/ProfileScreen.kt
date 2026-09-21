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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.openxiot.wematrix.AppLocale
import cc.openxiot.wematrix.AppState
import cc.openxiot.wematrix.R
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
    onNavigateToModbus: () -> Unit = {},
    onNavigateToAlarm: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToLanguage: () -> Unit = {}
) {
    val tokenManager = WeMatrixApp.instance.tokenManager

    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle(title = stringResource(R.string.tab_profile))

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
                                text = tokenManager.username ?: stringResource(R.string.profile_not_logged_in),
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
                    SectionHeader(stringResource(R.string.profile_current_org))
                    SettingsCard(
                        title = currentOrgName ?: stringResource(R.string.profile_no_org_selected),
                        subtitle = stringResource(R.string.profile_org_subtitle),
                        icon = Icons.Default.Group,
                        onClick = onNavigateToOrgPicker
                    )
                }
            }

            // Current project（始终显示；组织禁用时也可直接进入项目选择）
            item {
                SectionHeader(stringResource(R.string.profile_current_project))
                SettingsCard(
                    title = currentProjectName ?: stringResource(R.string.profile_no_project_selected),
                    subtitle = when {
                        !organizationEnabled -> stringResource(R.string.profile_project_subtitle_pick)
                        currentOrgName != null -> stringResource(R.string.profile_project_subtitle_switch)
                        else -> stringResource(R.string.profile_project_subtitle_org_first)
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
                SectionHeader(stringResource(R.string.profile_section_settings))
                SettingsCard(
                    title = stringResource(
                        if (AppState.isDarkMode) R.string.profile_dark_mode else R.string.profile_light_mode
                    ),
                    subtitle = stringResource(R.string.profile_theme_subtitle),
                    icon = if (AppState.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                    showChevron = false,
                    onClick = { AppState.toggleDarkMode(tokenManager) }
                )
                // 副标题显示当前选择，与上面深色模式那张卡同一个路子（标题说是什么，副标题说现在是什么）
                SettingsCard(
                    title = stringResource(R.string.profile_language_title),
                    subtitle = stringResource(languageLabelRes(AppLocale.current)),
                    icon = Icons.Default.Language,
                    onClick = onNavigateToLanguage
                )
            }

            // 其他：四张卡的顺序对齐 web 的侧边栏 —— 设备点表 / 告警 / 历史，最后才是「关于」
            // （「关于」是这一页自己的事，不属于功能入口，放最底下）。
            // 只有设备点表跟「已启用组织」这个开关走（不看管理员、不看 DTU）——
            // 告警与历史在 web 的侧边栏上没有这个门，故照样不设
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(stringResource(R.string.profile_section_other))
                if (organizationEnabled) {
                    SettingsCard(
                        title = stringResource(R.string.profile_modbus_title),
                        subtitle = stringResource(R.string.profile_modbus_subtitle),
                        icon = Icons.Default.TableChart,
                        onClick = onNavigateToModbus
                    )
                }
                SettingsCard(
                    title = stringResource(R.string.profile_alarm_title),
                    subtitle = stringResource(R.string.profile_alarm_subtitle),
                    icon = Icons.Default.Notifications,
                    onClick = onNavigateToAlarm
                )
                SettingsCard(
                    title = stringResource(R.string.profile_history_title),
                    subtitle = stringResource(R.string.profile_history_subtitle),
                    icon = Icons.Default.History,
                    onClick = onNavigateToHistory
                )
                SettingsCard(
                    title = stringResource(R.string.about_title),
                    subtitle = stringResource(R.string.profile_about_subtitle),
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

