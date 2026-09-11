package cc.openxiot.wematrix.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import cc.openxiot.wematrix.data.api.DeviceEntity
import cc.openxiot.wematrix.data.api.ModbusServiceBrief
import cc.openxiot.wematrix.data.api.SpaceEntity
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import cc.openxiot.wematrix.ui.components.ErrorMessage
import cc.openxiot.wematrix.ui.main.PageTitle
import cc.openxiot.wematrix.ui.modbus.ModbusServiceRow
import cc.openxiot.wematrix.ui.project.ProjectViewModel
import cc.openxiot.wematrix.ui.project.buildDeviceChildren
import cc.openxiot.wematrix.ui.project.deviceDids
import cc.openxiot.wematrix.ui.project.groupServicesByDid
import cc.openxiot.wematrix.ui.project.isDeviceTreeRoot
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    rootId: String?,
    onDeviceDetail: ((String) -> Unit)? = null,
    onDeviceOperation: ((did: String, type: String, spaceId: String) -> Unit)? = null,
    onServiceClick: ((spaceId: String, serviceId: String) -> Unit)? = null,
    projectViewModel: ProjectViewModel = viewModel()
) {
    val treeState by projectViewModel.treeState.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }

    // 子设备 / 服务都从空间图这张扁平表里按 did 现分组（后端没有 children 字段，见 DeviceTree.kt）
    val deviceChildren = remember(treeState.devices) { buildDeviceChildren(treeState.devices) }
    val deviceIds = remember(treeState.devices) { deviceDids(treeState.devices) }
    val servicesByDid = remember(treeState.services) {
        groupServicesByDid(treeState.services) { it.did }
    }
    // 拍平成按 parentId 缩进的行：LazyColumn 铺不了嵌套结构，先摊平（同 web 的 flattenDeviceRows）
    val rows = remember(treeState.devices, treeState.expandedDeviceIds, servicesByDid) {
        flattenDeviceRows(
            devices = treeState.devices,
            deviceChildren = deviceChildren,
            deviceIds = deviceIds,
            servicesByDid = servicesByDid,
            expandedDeviceIds = treeState.expandedDeviceIds
        )
    }

    LaunchedEffect(rootId) {
        if (rootId != null) {
            projectViewModel.loadSpaceGraph(rootId)
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing && rootId != null) {
            projectViewModel.loadSpaceGraphInternal(rootId)
            isRefreshing = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle(title = "设备")

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            modifier = Modifier.weight(1f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (rootId == null) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize().fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "请先在「我」的页面选择项目",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (treeState.isLoading && treeState.devices.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize().fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (treeState.error != null && treeState.devices.isEmpty()) {
                        item {
                            LaunchedEffect(treeState.error) {
                                projectViewModel.clearTreeError()
                            }
                            Box(modifier = Modifier.fillParentMaxSize().fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = treeState.error!!,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    OutlinedButton(onClick = { projectViewModel.loadSpaceGraph(rootId) }) {
                                        Text("重试")
                                    }
                                }
                            }
                        }
                    } else {
                        if (treeState.devices.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillParentMaxSize().fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "暂无设备",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            item {
                                Text(
                                    text = "共 ${treeState.devices.size} 个设备",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                )
                            }
                            items(rows) { row ->
                                when (row) {
                                    is DeviceListRow.DeviceRow -> DeviceCard(
                                        device = row.device,
                                        productNames = treeState.productNames,
                                        productIcons = treeState.productIcons,
                                        rootSpace = treeState.rootSpace,
                                        depth = row.depth,
                                        hasNested = row.hasNested,
                                        isExpanded = row.isExpanded,
                                        onToggle = row.device.did?.let { did ->
                                            { projectViewModel.toggleDeviceExpanded(did) }
                                        },
                                        onClick = row.device.did?.let { did ->
                                            {
                                                onDeviceOperation?.invoke(
                                                    did,
                                                    row.device.type ?: "",
                                                    row.device.space?.spaceId ?: rootId ?: ""
                                                )
                                            }
                                        },
                                        onDetail = row.device.did?.let { did ->
                                            { onDeviceDetail?.invoke(did) }
                                        }
                                    )

                                    // 展开后挂在该设备下的服务：和设备卡片一样只是入口，点进服务详情
                                    is DeviceListRow.ServiceRow -> ModbusServiceRow(
                                        service = row.service,
                                        depth = row.depth,
                                        baseIndent = 16.dp,
                                        // 本页设备卡片是 16dp 内边距 + 36dp 图标 = 68dp，服务行照它撑高
                                        minHeight = 68.dp
                                    ) {
                                        // 这里 rootId 已被上面的分支判成非空
                                        row.service.id?.let { onServiceClick?.invoke(rootId, it) }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

/**
 * 设备列表页的一行：设备本身，或（设备展开后）挂在该设备下的服务。
 *
 * 与 web 的 `DeviceRow` 同一个思路 —— 那一层也要区分「设备行」和「服务行」。
 */
private sealed interface DeviceListRow {
    val depth: Int

    data class DeviceRow(
        val device: DeviceEntity,
        override val depth: Int,
        val hasNested: Boolean,
        val isExpanded: Boolean
    ) : DeviceListRow

    data class ServiceRow(
        val service: ModbusServiceBrief,
        override val depth: Int
    ) : DeviceListRow
}

/**
 * 把扁平设备表拍成「按 parentId 缩进」的行列表。
 *
 * - 顶层设备 = 没有父设备 / 父设备是自己 / 父设备不在本表里（断链当顶层，免得整条支路消失）；
 * - 有子设备**或**有服务的设备才带展开箭头；
 * - 展开后先服务行、后子设备（顺序对齐 web 的 flattenDeviceRows）；
 * - `visited` 挡环：数据里理论上不该有自环，真出现时别把界面拖死。
 */
private fun flattenDeviceRows(
    devices: List<DeviceEntity>,
    deviceChildren: Map<String, List<DeviceEntity>>,
    deviceIds: Set<String>,
    servicesByDid: Map<String, List<ModbusServiceBrief>>,
    expandedDeviceIds: Set<String>
): List<DeviceListRow> {
    val rows = mutableListOf<DeviceListRow>()
    val visited = mutableSetOf<String>()

    fun push(device: DeviceEntity, depth: Int) {
        val did = device.did ?: return
        if (!visited.add(did)) return

        val children = deviceChildren[did].orEmpty()
        val services = servicesByDid[did].orEmpty()
        val hasNested = children.isNotEmpty() || services.isNotEmpty()
        val isExpanded = hasNested && expandedDeviceIds.contains(did)

        rows += DeviceListRow.DeviceRow(device, depth, hasNested, isExpanded)
        if (!isExpanded) return

        services.forEach { rows += DeviceListRow.ServiceRow(it, depth + 1) }
        children.forEach { push(it, depth + 1) }
    }

    devices.filter { isDeviceTreeRoot(it, deviceIds) }.forEach { push(it, 0) }
    return rows
}

@Composable
private fun DeviceCard(
    device: DeviceEntity,
    productNames: Map<String, String>,
    productIcons: Map<String, String>,
    rootSpace: SpaceEntity?,
    depth: Int = 0,
    /** 有子设备或有服务：卡片左边给一个展开箭头 */
    hasNested: Boolean = false,
    isExpanded: Boolean = false,
    onToggle: (() -> Unit)? = null,
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
            .padding(start = (16 + depth * 20).dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
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
            // 展开箭头：点卡片是「设备操作」，所以只有这一小块管展开，卡片本身不跟着切换。
            // 触摸区对齐右边那颗「详情」chevron —— 48dp 宽 × 整卡高；小图标十几 dp，手指按不准。
            if (hasNested) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(48.dp)
                        .clickable { onToggle?.invoke() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            // 左内边距让给上面的箭头槽了，右边距保持不变
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 0.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    if (productIcon != null) {
                        AsyncImage(
                            model = productIcon,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.DevicesOther,
                                contentDescription = null,
                                tint = if (device.online == true) MaterialTheme.colorScheme.onSecondaryContainer
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
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
