package cc.openxiot.wematrix.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import cc.openxiot.wematrix.WeMatrixApp
import cc.openxiot.wematrix.data.api.RetrofitClient
import cc.openxiot.wematrix.data.repository.UserSettingsRepository
import cc.openxiot.wematrix.ui.components.AvatarImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val tokenManager = WeMatrixApp.instance.tokenManager
    val settingsRepository = remember { UserSettingsRepository() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var organizationEnabled by remember { mutableStateOf(tokenManager.organizationEnabled) }

    // 打开页面时从服务器同步一次设置（可能在其他端改过）
    LaunchedEffect(Unit) {
        settingsRepository.getSettings().onSuccess { settings ->
            organizationEnabled = settings.organizationEnabled
            tokenManager.organizationEnabled = settings.organizationEnabled
        }
    }

    // 选中即保存；失败回滚；禁用成功后清空已选组织与项目
    fun toggleOrganization(enabled: Boolean) {
        val previous = organizationEnabled
        organizationEnabled = enabled
        tokenManager.organizationEnabled = enabled
        scope.launch {
            settingsRepository.updateSettings(enabled)
                .onSuccess {
                    if (!enabled) {
                        tokenManager.clearCurrentSelection()
                        RetrofitClient.setOrgId(null)
                    }
                }
                .onFailure { e ->
                    organizationEnabled = previous
                    tokenManager.organizationEnabled = previous
                    Toast.makeText(context, e.message ?: "更新设置失败", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text(
                        text = "账号详情",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Profile header
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        size = 64.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = tokenManager.username ?: "未登录",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tokenManager.platform ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Account info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    InfoRow(label = "用户名", value = tokenManager.username ?: "-")
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    InfoRow(label = "平台", value = tokenManager.platform ?: "-")
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    InfoRow(label = "当前组织", value = tokenManager.currentOrgName ?: "未选择")
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    InfoRow(label = "当前项目", value = tokenManager.currentRootSpaceName ?: "未选择")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Settings: 组织启用开关（选中即保存）
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "组织",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "启用后可在「我」页面选择当前项目",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = organizationEnabled,
                        onCheckedChange = { enabled -> toggleOrganization(enabled) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Spacer(modifier = Modifier.weight(1f))

            // Logout button (entire card clickable)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        tokenManager.clear()
                        onLogout()
                    },
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
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "退出登录",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
