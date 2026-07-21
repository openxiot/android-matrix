package cc.openxiot.android.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.android.data.api.DeviceEntity
import cc.openxiot.android.ui.components.EmptyState
import cc.openxiot.android.ui.components.LoadingIndicator
import cc.openxiot.android.ui.components.OnlineIndicator
import cc.openxiot.android.ui.components.ErrorMessage
import androidx.compose.ui.text.font.FontWeight
import cc.openxiot.android.ui.main.PageTitle
import cc.openxiot.android.ui.project.ProjectViewModel

@Composable
fun DeviceListScreen(
    rootId: String?,
    projectViewModel: ProjectViewModel = viewModel()
) {
    val treeState by projectViewModel.treeState.collectAsState()

    LaunchedEffect(rootId) {
        if (rootId != null) {
            projectViewModel.loadSpaceGraph(rootId)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle(title = "设备")

        when {
            rootId == null -> EmptyState(message = "请先在「我」的页面选择项目")
            treeState.isLoading -> LoadingIndicator()
            treeState.error != null -> {
                LaunchedEffect(treeState.error) {
                    projectViewModel.clearTreeError()
                }
                ErrorMessage(
                    message = treeState.error!!,
                    onRetry = { projectViewModel.loadSpaceGraph(rootId) }
                )
            }
            treeState.devices.isEmpty() -> EmptyState(message = "暂无设备")
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item {
                        Text(
                            text = "共 ${treeState.devices.size} 个设备",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    items(treeState.devices) { device ->
                        DeviceCard(device = device)
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
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
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (device.online == true)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.DevicesOther,
                        contentDescription = null,
                        tint = if (device.online == true)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.did ?: "未知设备",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.type ?: "未分类",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (device.protocol != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = device.protocol,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OnlineIndicator(isOnline = device.online == true)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (device.online == true) "在线" else "离线",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (device.online == true) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
