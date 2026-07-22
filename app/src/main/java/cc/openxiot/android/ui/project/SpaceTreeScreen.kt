package cc.openxiot.android.ui.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.android.data.api.DeviceEntity
import cc.openxiot.android.data.api.SpaceEntity
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import cc.openxiot.android.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceTreeScreen(
    rootId: String,
    onBack: () -> Unit,
    viewModel: ProjectViewModel = viewModel()
) {
    val treeState by viewModel.treeState.collectAsState()
    val projectState by viewModel.projectState.collectAsState()

    LaunchedEffect(rootId) {
        viewModel.loadSpaceGraph(rootId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = treeState.rootSpace?.name ?: projectState.currentRootName ?: "空间管理",
                            fontWeight = FontWeight.Bold
                        )
                        treeState.rootSpace?.let { root ->
                            Text(
                                text = "空间 • ${root.type ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = "添加")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("添加空间") },
                                onClick = {
                                    showMenu = false
                                    viewModel.showCreateDialog(rootId)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("添加设备") },
                                onClick = {
                                    showMenu = false
                                    viewModel.showAddDeviceDialog()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        SpaceTreeContent(
            rootId = rootId,
            viewModel = viewModel,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
fun SpaceTreeContent(
    rootId: String,
    viewModel: ProjectViewModel,
    modifier: Modifier = Modifier,
    showActions: Boolean = true
) {
    val treeState by viewModel.treeState.collectAsState()
    // Load graph when rootId changes, calling suspend function directly
    LaunchedEffect(rootId) {
        viewModel.loadSpaceGraphInternal(rootId)
    }

    Box(modifier = modifier) {
        when {
            treeState.isLoading -> LoadingIndicator()
            treeState.error != null -> ErrorMessage(
                message = treeState.error!!,
                onRetry = { viewModel.loadSpaceGraph(rootId) }
            )
            treeState.rootSpace == null -> EmptyState(message = "空间数据为空")
            treeState.rootSpace?.children.isNullOrEmpty() && treeState.devices.isEmpty() -> EmptyState("请添加空间")
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    treeState.rootSpace?.children?.forEach { child ->
                        item {
                            RecursiveSpaceTree(
                                space = child,
                                depth = 0,
                                expandedIds = treeState.expandedIds,
                                onToggle = { viewModel.toggleExpanded(it) },
                                onAddChild = { viewModel.showCreateDialog(it) },
                                onDelete = { viewModel.showDeleteConfirm(it) },
                                rootId = rootId,
                                devices = treeState.devices,
                                showActions = showActions
                            )
                        }
                    }

                    if (treeState.devices.isNotEmpty()) {
                        item { Spacer(Modifier.height(8.dp)) }
                        item { SectionHeader("设备列表 (${treeState.devices.size})") }
                        items(treeState.devices) { device ->
                            DeviceItem(device = device)
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Create space dialog
    if (treeState.showCreateDialog) {
        var spaceName by remember { mutableStateOf("") }
        val parentSpace = remember(treeState.createParentId, treeState.rootSpace) {
            treeState.createParentId?.let { parentId ->
                findSpaceById(treeState.rootSpace, parentId)
            }
        }
        val defaultType = when (parentSpace?.type) {
            "building" -> "floor"
            "floor" -> "room"
            "room" -> "zone"
            else -> "building"
        }
        var spaceType by remember(treeState.showCreateDialog) { mutableStateOf(defaultType) }
        val types = listOf(
            "building" to "楼栋",
            "floor" to "楼层",
            "room" to "房间",
            "zone" to "区域"
        )
        AlertDialog(
            onDismissRequest = { viewModel.hideCreateDialog() },
            title = { Text("添加空间") },
            text = {
                Column {
                    OutlinedTextField(
                        value = spaceName,
                        onValueChange = { spaceName = it },
                        label = { Text("空间名称") },
                        placeholder = { Text("请输入名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("空间类型", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    types.forEach { (type, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { spaceType = type }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = spaceType == type,
                                onClick = { spaceType = type }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createSpace(
                            name = spaceName,
                            type = spaceType,
                            parentId = treeState.createParentId,
                            rootId = rootId
                        )
                    },
                    enabled = spaceName.isNotBlank()
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideCreateDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    // Delete confirm
    treeState.showDeleteConfirm?.let { spaceId ->
        ConfirmDialog(
            title = "删除空间",
            message = "确定要删除这个空间吗？如果空间下有子空间，将无法删除。",
            onConfirm = { viewModel.deleteSpace(spaceId, rootId) },
            onDismiss = { viewModel.hideDeleteConfirm() }
        )
    }

    // QR scanner for adding devices
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.addDeviceByQr(rootId, result.contents)
        } else {
            viewModel.hideAddDeviceDialog()
        }
    }
    LaunchedEffect(treeState.showAddDeviceDialog) {
        if (treeState.showAddDeviceDialog) {
            qrScanner.launch(ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("扫描设备二维码")
            })
        }
    }
}

@Composable
private fun RecursiveSpaceTree(
    space: SpaceEntity,
    depth: Int,
    expandedIds: Set<String>,
    onToggle: (String) -> Unit,
    onAddChild: (String) -> Unit,
    onDelete: (String) -> Unit,
    rootId: String,
    devices: List<DeviceEntity>,
    showActions: Boolean
) {
    SpaceTreeNode(
        space = space,
        depth = depth,
        isExpanded = expandedIds.contains(space.id),
        onToggle = { space.id?.let { onToggle(it) } },
        onAddChild = { space.id?.let { onAddChild(it) } },
        onDelete = { space.id?.let { onDelete(it) } },
        devices = devices,
        showActions = showActions
    )
    AnimatedVisibility(visible = expandedIds.contains(space.id)) {
        Column {
            space.children?.forEach { child ->
                RecursiveSpaceTree(
                    space = child,
                    depth = depth + 1,
                    expandedIds = expandedIds,
                    onToggle = onToggle,
                    onAddChild = onAddChild,
                    onDelete = onDelete,
                    rootId = rootId,
                    devices = devices,
                    showActions = showActions
                )
            }
        }
    }
}

@Composable
private fun SpaceTreeNode(
    space: SpaceEntity,
    depth: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onAddChild: () -> Unit,
    onDelete: () -> Unit,
    devices: List<DeviceEntity>,
    showActions: Boolean = true
) {
    val hasChildren = space.children?.isNotEmpty() == true
    val spaceDevices = devices.filter { d ->
        d.did?.let { did -> space.devices?.any { sd -> sd.did == did } == true } ?: false
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = (12 + depth * 20).dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 4.dp
            )
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand icon
            if (hasChildren) {
                Icon(
                    if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(20.dp))
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Type icon
            val typeIcon = when (space.type?.lowercase()) {
                "site" -> Icons.Default.Business
                "building" -> Icons.Default.Apartment
                "floor" -> Icons.Default.ViewAgenda
                "room" -> Icons.Default.MeetingRoom
                else -> Icons.Default.Place
            }
            Icon(
                typeIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = space.name ?: "未命名",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpaceTypeChip(type = space.type ?: "other")
                    if (spaceDevices.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${spaceDevices.size} 设备",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Actions
            if (showActions) {
                IconButton(
                    onClick = onAddChild,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加子空间", modifier = Modifier.size(18.dp))
                }
                if (depth > 0) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(device: DeviceEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DevicesOther,
                contentDescription = null,
                tint = if (device.online == true) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.did ?: "未知设备",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
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

/**
 * Find a space by id in the space tree, searching recursively through children.
 */
private fun findSpaceById(root: SpaceEntity?, id: String): SpaceEntity? {
    if (root == null) return null
    if (root.id == id) return root
    return root.children?.firstNotNullOfOrNull { findSpaceById(it, id) }
}
