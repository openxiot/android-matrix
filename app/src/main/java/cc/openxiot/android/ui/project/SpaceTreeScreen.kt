package cc.openxiot.android.ui.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
                                showActions = showActions,
                                onDeviceClick = { viewModel.showMoveDevice(it) },
                                productNames = treeState.productNames
                            )
                        }
                    }

                    // Render root space's devices (devices moved directly to root)
                    val rootDevices = treeState.devices.filter { it.space?.spaceId == treeState.rootSpace?.id }
                    rootDevices.forEach { device ->
                        item {
                            DeviceItem(
                                device = device,
                                onClick = device.did?.let { did -> { viewModel.showMoveDevice(did) } },
                                depth = 0,
                                productNames = treeState.productNames
                            )
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
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
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
                setOrientationLocked(true)
                setPrompt("扫描设备二维码")
            })
        }
    }

    // Show operation feedback messages
    val context = LocalContext.current
    LaunchedEffect(treeState.message) {
        treeState.message?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearTreeMessage()
        }
    }

    // Loading dialog with countdown when adding device
    if (treeState.isAddingDevice) {
        var seconds by remember { mutableIntStateOf(0) }
        LaunchedEffect(treeState.isAddingDevice) {
            while (treeState.isAddingDevice) {
                kotlinx.coroutines.delay(1000)
                seconds++
            }
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("添加设备中...") },
            text = { Text("等待中... ${seconds}s") },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {}
        )
    }

    // Move device dialog
    treeState.showMoveDeviceDialog?.let { did ->
        var selectedSpaceId by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { viewModel.hideMoveDevice() },
            title = { Text("移动到空间") },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())
                ) {
                    treeState.rootSpace?.let { root ->
                        SpacePickerItem(
                            space = root,
                            depth = 0,
                            selectedSpaceId = selectedSpaceId,
                            onSelect = { selectedSpaceId = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { selectedSpaceId?.let { viewModel.moveDeviceTo(it, did) } },
                    enabled = selectedSpaceId != null
                ) { Text("移动") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideMoveDevice() }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SpacePickerItem(
    space: SpaceEntity,
    depth: Int,
    selectedSpaceId: String?,
    onSelect: (String) -> Unit
) {
    val hasChildren = space.children?.isNotEmpty() == true
    var expanded by remember { mutableStateOf(true) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { space.id?.let { onSelect(it) } }
            .padding(start = (16 + depth * 24).dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasChildren) {
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Spacer(Modifier.width(24.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = space.name ?: space.id ?: "未知",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        RadioButton(
            selected = selectedSpaceId == space.id,
            onClick = { space.id?.let { onSelect(it) } }
        )
    }
    if (hasChildren && expanded) {
        space.children?.forEach { child ->
            SpacePickerItem(
                space = child,
                depth = depth + 1,
                selectedSpaceId = selectedSpaceId,
                onSelect = onSelect
            )
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
    showActions: Boolean,
    onDeviceClick: ((String) -> Unit)? = null,
    productNames: Map<String, String> = emptyMap()
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
                    showActions = showActions,
                    onDeviceClick = onDeviceClick,
                    productNames = productNames
                )
            }
            // Render devices of this space inline
            val spaceDevices = devices.filter { it.space?.spaceId == space.id }
            spaceDevices.forEach { device ->
                DeviceItem(
                    device = device,
                    onClick = device.did?.let { did -> { onDeviceClick?.invoke(did) } },
                    depth = depth + 1,
                    productNames = productNames
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
    val spaceDevices = devices.filter { it.space?.spaceId == space.id }
    val hasExpandable = space.children?.isNotEmpty() == true || spaceDevices.isNotEmpty()

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
            if (hasExpandable) {
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
private fun DeviceItem(
    device: DeviceEntity,
    onClick: (() -> Unit)? = null,
    depth: Int = 0,
    productNames: Map<String, String> = emptyMap()
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (12 + depth * 20).dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
                    text = productNames[device.type] ?: device.type ?: device.did ?: "未知设备",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
 * Flatten a space tree into a list of all spaces.
 */
private fun buildFlatSpaceList(root: SpaceEntity?): List<SpaceEntity> {
    if (root == null) return emptyList()
    val result = mutableListOf(root)
    root.children?.forEach { child ->
        result.addAll(buildFlatSpaceList(child))
    }
    return result
}

/**
 * Find a space by id in the space tree, searching recursively through children.
 */
private fun findSpaceById(root: SpaceEntity?, id: String): SpaceEntity? {
    if (root == null) return null
    if (root.id == id) return root
    return root.children?.firstNotNullOfOrNull { findSpaceById(it, id) }
}
