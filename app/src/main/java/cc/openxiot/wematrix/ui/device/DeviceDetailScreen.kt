package cc.openxiot.wematrix.ui.device

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.data.api.SpaceEntity
import cc.openxiot.wematrix.ui.project.ProjectViewModel
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import cc.openxiot.wematrix.ui.components.OnlineIndicator
import cc.openxiot.wematrix.ui.modbus.DeviceServicesCard
import cc.openxiot.wematrix.ui.modbus.ModbusServiceViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    did: String,
    onBack: () -> Unit,
    /** 点服务行：传当前根空间（服务接口的鉴权作用域）与服务 id */
    onServiceClick: ((spaceId: String, serviceId: String) -> Unit)? = null,
    viewModel: ProjectViewModel = viewModel(),
    serviceViewModel: ModbusServiceViewModel = viewModel()
) {
    val treeState by viewModel.treeState.collectAsState()
    val projectState by viewModel.projectState.collectAsState()
    val servicesState by serviceViewModel.deviceServices.collectAsState()
    // 服务接口路径里的 spaceId 是鉴权作用域，统一用当前项目根空间（口径同 web）
    val rootSpaceId = projectState.currentRootId
    val device = treeState.devices.find { it.did == did }
    val model = device?.type?.let { extractModelFromUrn(it) }
    val productName = model?.let { treeState.productNames[it] }
    val productIcon = model?.let { treeState.productIcons[it] }

    // 服务按 did 取（/parent 只按 did 过滤），根空间只作鉴权作用域
    LaunchedEffect(did, rootSpaceId) {
        if (rootSpaceId != null) serviceViewModel.loadByDevice(rootSpaceId, did)
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
                        text = productName ?: "设备详情",
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (device == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("设备未找到", style = MaterialTheme.typography.bodyLarge)
                }
                return@Scaffold
            }

            // Status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            if (productIcon != null) {
                                AsyncImage(
                                    model = productIcon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.DevicesOther,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = productName ?: "未知产品",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OnlineIndicator(isOnline = device.online == true)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (device.online == true) "在线" else "离线",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (device.online == true) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Device ID
            DetailRow(label = "设备 ID", value = device.did ?: "-")
            HorizontalDivider()

            // Device type
            val typeName = extractTypeName(device.type) ?: device.type ?: "-"
            DetailRow(label = "设备类型", value = typeName)
            HorizontalDivider()

            // Protocol
            DetailRow(label = "通信协议", value = device.protocol ?: "-")
            HorizontalDivider()

            // Online status
            DetailRow(label = "在线状态", value = if (device.online == true) "在线" else "离线")
            HorizontalDivider()

            // Last online
            if (device.lastOnline != null) {
                DetailRow(label = "最后上线", value = formatUtcToLocal(device.lastOnline))
                HorizontalDivider()
            }

            // Last offline
            if (device.lastOffline != null) {
                DetailRow(label = "最后下线", value = formatUtcToLocal(device.lastOffline))
                HorizontalDivider()
            }

            // Space
            if (device.space?.spaceId != null) {
                val spaceName = findSpaceById(treeState.rootSpace, device.space.spaceId)?.name
                DetailRow(label = "所属空间", value = spaceName ?: device.space.spaceId ?: "-")
            }

            // Services（只读入口：点进服务详情页调用方法；新建/编辑/删除在移动端不做）
            DeviceServicesCard(
                services = servicesState.services,
                isLoading = servicesState.isLoading && rootSpaceId != null,
                error = servicesState.error,
                onRetry = { rootSpaceId?.let { serviceViewModel.loadByDevice(it, did) } },
                onServiceClick = { serviceId ->
                    rootSpaceId?.let { onServiceClick?.invoke(it, serviceId) }
                }
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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

/**
 * Extract the model field from a device type URN.
 * Format: urn:<ns>:device:<name>:<value>:<organization>:<model>:<version>
 */
private fun extractModelFromUrn(urn: String?): String? {
    if (urn == null) return null
    val parts = urn.split(":")
    return if (parts.size >= 7) parts[6] else null
}

/**
 * Extract the human-readable type name (4th field) from a device type URN.
 */
private fun extractTypeName(urn: String?): String? {
    if (urn == null) return null
    val parts = urn.split(":")
    return if (parts.size >= 4) parts[3] else null
}

private fun formatUtcToLocal(utcTime: String?): String {
    if (utcTime == null) return "-"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(utcTime)
        if (date != null) {
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            outputFormat.timeZone = TimeZone.getDefault()
            outputFormat.format(date)
        } else utcTime
    } catch (_: Exception) { utcTime }
}

private fun findSpaceById(root: SpaceEntity?, id: String?): SpaceEntity? {
    if (root == null || id == null) return null
    if (root.id == id) return root
    return root.children?.firstNotNullOfOrNull { findSpaceById(it, id) }
}
