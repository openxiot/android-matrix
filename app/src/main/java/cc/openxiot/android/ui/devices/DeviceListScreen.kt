package cc.openxiot.android.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import cc.openxiot.android.data.api.DeviceEntity
import cc.openxiot.android.data.api.SpaceEntity
import cc.openxiot.android.ui.components.EmptyState
import cc.openxiot.android.ui.components.LoadingIndicator
import cc.openxiot.android.ui.components.OnlineIndicator
import cc.openxiot.android.ui.components.ErrorMessage
import cc.openxiot.android.ui.main.PageTitle
import cc.openxiot.android.ui.project.ProjectViewModel

@Composable
fun DeviceListScreen(
    rootId: String?,
    onDeviceDetail: ((String) -> Unit)? = null,
    onDeviceOperation: ((did: String, type: String, spaceId: String) -> Unit)? = null,
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
                        DeviceCard(
                            device = device,
                            productNames = treeState.productNames,
                            productIcons = treeState.productIcons,
                            rootSpace = treeState.rootSpace,
                            onClick = device.did?.let { did ->
                                { onDeviceOperation?.invoke(did, device.type ?: "", device.space?.spaceId ?: rootId ?: "") }
                            },
                            onDetail = device.did?.let { did -> { onDeviceDetail?.invoke(did) } }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceEntity,
    productNames: Map<String, String>,
    productIcons: Map<String, String>,
    rootSpace: SpaceEntity?,
    onClick: (() -> Unit)? = null,
    onDetail: (() -> Unit)? = null
) {
    val model = extractModelFromUrn(device.type)
    val productName = model?.let { productNames[it] }
    val productIcon = model?.let { productIcons[it] }
    val spaceName = device.space?.spaceId?.let { findSpaceById(rootSpace, it) }?.name

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (productIcon != null) {
                    AsyncImage(
                        model = productIcon,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        Icons.Default.DevicesOther,
                        contentDescription = null,
                        tint = if (device.online == true) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dotColor = if (device.online == true) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.error
                        Canvas(modifier = Modifier.size(8.dp)) {
                            drawCircle(color = dotColor)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = productName ?: device.type ?: device.did ?: "未知设备",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (spaceName != null) {
                        Text(
                            text = spaceName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (onDetail != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(48.dp)
                        .clickable(onClick = onDetail),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "详情",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun extractModelFromUrn(urn: String?): String? {
    if (urn == null) return null
    val parts = urn.split(":")
    return if (parts.size >= 7) parts[6] else null
}

private fun findSpaceById(root: SpaceEntity?, id: String?): SpaceEntity? {
    if (root == null || id == null) return null
    if (root.id == id) return root
    return root.children?.firstNotNullOfOrNull { findSpaceById(it, id) }
}
