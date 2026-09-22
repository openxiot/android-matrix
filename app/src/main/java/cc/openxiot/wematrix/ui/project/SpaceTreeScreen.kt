package cc.openxiot.wematrix.ui.project

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.R
import cc.openxiot.wematrix.data.api.DeviceEntity
import cc.openxiot.wematrix.data.api.ModbusServiceBrief
import cc.openxiot.wematrix.data.api.SpaceEntity
import cc.openxiot.wematrix.ui.modbus.ModbusServiceRow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.ContentScale
import android.app.Activity
import android.content.Intent
import cc.openxiot.wematrix.ui.scan.ScanQrActivity
import cc.openxiot.wematrix.ui.components.ConfirmDialog
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.ErrorMessage
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import cc.openxiot.wematrix.ui.components.SpaceTypeChip
import cc.openxiot.wematrix.ui.core.asString
import cc.openxiot.wematrix.util.mirrorInRtl
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.collections.get

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceTreeScreen(
    rootId: String,
    onBack: () -> Unit,
    onDeviceDetail: ((String) -> Unit)? = null,
    onDeviceOperation: ((did: String, type: String, spaceId: String) -> Unit)? = null,
    /** 点服务行：传当前根空间（服务接口的鉴权作用域）与服务 id */
    onServiceClick: ((spaceId: String, serviceId: String) -> Unit)? = null,
    viewModel: ProjectViewModel = viewModel()
) {
    val treeState by viewModel.treeState.collectAsState()
    val projectState by viewModel.projectState.collectAsState()

    LaunchedEffect(rootId) {
        viewModel.loadSpaceGraph(rootId)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    Text(
                        text = treeState.rootSpace?.name ?: projectState.currentRootName
                        ?: stringResource(R.string.space_manage_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        floatingActionButton = {
            var expanded by remember { mutableStateOf(false) }
            Column(
                horizontalAlignment = Alignment.End
            ) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        // Add device
                        SmallFloatingActionButton(
                            onClick = {
                                expanded = false
                                viewModel.showAddDeviceDialog()
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.space_add_device),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))

                        // Add space
                        SmallFloatingActionButton(
                            onClick = {
                                expanded = false
                                viewModel.showCreateDialog(rootId)
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.space_add),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                FloatingActionButton(
                    onClick = { expanded = !expanded },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        if (expanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = stringResource(R.string.common_add)
                    )
                }
            }
        }
    ) { padding ->
        SpaceTreeContent(
            rootId = rootId,
            viewModel = viewModel,
            modifier = Modifier.padding(padding),
            onDeviceDetail = onDeviceDetail,
            onDeviceOperation = onDeviceOperation,
            onServiceClick = onServiceClick
        )
    }
}

@Composable
fun SpaceTreeContent(
    rootId: String,
    viewModel: ProjectViewModel,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
    onDeviceDetail: ((String) -> Unit)? = null,
    onDeviceOperation: ((did: String, type: String, spaceId: String) -> Unit)? = null,
    onServiceClick: ((spaceId: String, serviceId: String) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp)
) {
    val treeState by viewModel.treeState.collectAsState()
    // Load graph when rootId changes, calling suspend function directly
    LaunchedEffect(rootId) {
        viewModel.loadSpaceGraphInternal(rootId)
    }

    // 子设备 / 服务都从空间图这张扁平表里按 did 现分组（后端没有 children 字段，见 DeviceTree.kt）。
    // 按 devices / services 缓存：图不变时不重复算。
    val deviceChildren = remember(treeState.devices) { buildDeviceChildren(treeState.devices) }
    val deviceIds = remember(treeState.devices) { deviceDids(treeState.devices) }
    val servicesByDid = remember(treeState.services) {
        groupServicesByDid(treeState.services) { it.did }
    }

    Box(modifier = modifier) {
        when {
            treeState.isLoading -> LoadingIndicator()
            treeState.error != null -> ErrorMessage(
                message = treeState.error!!,
                onRetry = { viewModel.loadSpaceGraph(rootId) }
            )
            treeState.rootSpace == null -> EmptyState(message = stringResource(R.string.space_empty))
            // 根空间自己不出行（它的名字在页面标题上）。所以「树是空的」= 没有任何空间和设备，
            // 这时两个入口给的提示不一样：编辑页能加空间，项目页（Tab）是只读的。
            treeState.rootSpace?.children.isNullOrEmpty() && treeState.devices.isEmpty() -> EmptyState(
                if (showActions) stringResource(R.string.space_empty_editable)
                else stringResource(R.string.space_empty_readonly)
            )
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding
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
                                deviceChildren = deviceChildren,
                                deviceIds = deviceIds,
                                servicesByDid = servicesByDid,
                                expandedDeviceIds = treeState.expandedDeviceIds,
                                onToggleDevice = { viewModel.toggleDeviceExpanded(it) },
                                showActions = showActions,
                                onDeviceDetail = onDeviceDetail,
                                onDeviceOperation = onDeviceOperation,
                                onServiceClick = onServiceClick,
                                productNames = treeState.productNames,
                                productIcons = treeState.productIcons
                            )
                        }
                    }

                    // Render root space's devices (devices moved directly to root).
                    // 只铺「顶层设备」：有父设备且在表里能找到的，改为挂在父设备下面（见 DeviceSubtree），
                    // 否则同一台设备会在空间层级和父设备下面各出现一次。
                    val rootDevices = treeState.devices.filter {
                        it.space?.spaceId == treeState.rootSpace?.id && isDeviceTreeRoot(it, deviceIds)
                    }
                    rootDevices.forEach { device ->
                        item {
                            DeviceSubtree(
                                device = device,
                                depth = 0,
                                deviceChildren = deviceChildren,
                                servicesByDid = servicesByDid,
                                expandedDeviceIds = treeState.expandedDeviceIds,
                                onToggleDevice = { viewModel.toggleDeviceExpanded(it) },
                                rootId = rootId,
                                productNames = treeState.productNames,
                                productIcons = treeState.productIcons,
                                onDeviceDetail = onDeviceDetail,
                                onDeviceOperation = onDeviceOperation,
                                onServiceClick = onServiceClick
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
            "building" to R.string.space_type_building,
            "floor" to R.string.space_type_floor,
            "room" to R.string.space_type_room,
            "zone" to R.string.space_type_zone
        )
        AlertDialog(
            onDismissRequest = { viewModel.hideCreateDialog() },
            title = { Text(stringResource(R.string.space_add)) },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.background,
            text = {
                Column {
                    OutlinedTextField(
                        value = spaceName,
                        onValueChange = { spaceName = it },
                        label = { Text(stringResource(R.string.space_name_label)) },
                        placeholder = { Text(stringResource(R.string.space_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.space_type_label), style = MaterialTheme.typography.labelLarge)
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
                            Text(stringResource(label))
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
                    Text(stringResource(R.string.common_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideCreateDialog() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Delete confirm
    treeState.showDeleteConfirm?.let { spaceId ->
        ConfirmDialog(
            title = stringResource(R.string.space_delete_title),
            message = stringResource(R.string.space_delete_confirm),
            onConfirm = { viewModel.deleteSpace(spaceId, rootId) },
            onDismiss = { viewModel.hideDeleteConfirm() }
        )
    }

    // QR/IMEI scanner for adding devices (custom portrait scan screen)
    val context = LocalContext.current
    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data?.getStringExtra(ScanQrActivity.EXTRA_RESULT)
        if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            viewModel.addDeviceByQr(rootId, text)
        } else {
            viewModel.hideAddDeviceDialog()
        }
    }
    LaunchedEffect(treeState.showAddDeviceDialog) {
        if (treeState.showAddDeviceDialog) {
            scannerLauncher.launch(Intent(context, ScanQrActivity::class.java))
        }
    }

    // Show operation feedback messages
    LaunchedEffect(treeState.message) {
        treeState.message?.let { msg ->
            Toast.makeText(context, msg.asString(context), Toast.LENGTH_SHORT).show()
            viewModel.clearTreeMessage()
        }
    }

    // Loading dialog with countdown when adding device
    if (treeState.isAddingDevice) {
        var seconds by remember { mutableIntStateOf(0) }
        LaunchedEffect(treeState.isAddingDevice) {
            while (treeState.isAddingDevice) {
                delay(1000)
                seconds++
            }
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.space_device_adding)) },
            text = { Text(stringResource(R.string.space_device_waiting, seconds)) },
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
            title = { Text(stringResource(R.string.space_move_title)) },
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
                ) { Text(stringResource(R.string.space_move_action)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideMoveDevice() }) { Text(stringResource(R.string.common_cancel)) }
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
                    modifier = Modifier.size(18.dp).mirrorInRtl()
                )
            }
        } else {
            Spacer(Modifier.width(24.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = space.name ?: space.id ?: stringResource(R.string.common_unknown),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (selectedSpaceId == space.id) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Spacer(Modifier.width(24.dp))
        }
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
    deviceChildren: Map<String, List<DeviceEntity>>,
    deviceIds: Set<String>,
    servicesByDid: Map<String, List<ModbusServiceBrief>>,
    expandedDeviceIds: Set<String>,
    onToggleDevice: (String) -> Unit,
    showActions: Boolean,
    onDeviceDetail: ((String) -> Unit)? = null,
    onDeviceOperation: ((did: String, type: String, spaceId: String) -> Unit)? = null,
    onServiceClick: ((spaceId: String, serviceId: String) -> Unit)? = null,
    productNames: Map<String, String?> = emptyMap(),
    productIcons: Map<String, String> = emptyMap()
) {
    SpaceTreeNode(
        space = space,
        depth = depth,
        isExpanded = expandedIds.contains(space.id),
        onToggle = { space.id?.let { onToggle(it) } },
        onAddChild = { space.id?.let { onAddChild(it) } },
        onDelete = { space.id?.let { onDelete(it) } },
        devices = devices,
        deviceIds = deviceIds,
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
                    deviceChildren = deviceChildren,
                    deviceIds = deviceIds,
                    servicesByDid = servicesByDid,
                    expandedDeviceIds = expandedDeviceIds,
                    onToggleDevice = onToggleDevice,
                    showActions = showActions,
                    onDeviceDetail = onDeviceDetail,
                    onDeviceOperation = onDeviceOperation,
                    onServiceClick = onServiceClick,
                    productNames = productNames,
                    productIcons = productIcons
                )
            }
            // 这个空间下直挂的设备。只铺顶层设备：有父设备的挂到父设备下面，避免同一个 did 出现两次
            val spaceDevices = devices.filter {
                it.space?.spaceId == space.id && isDeviceTreeRoot(it, deviceIds)
            }
            spaceDevices.forEach { device ->
                DeviceSubtree(
                    device = device,
                    depth = depth + 1,
                    deviceChildren = deviceChildren,
                    servicesByDid = servicesByDid,
                    expandedDeviceIds = expandedDeviceIds,
                    onToggleDevice = onToggleDevice,
                    rootId = rootId,
                    productNames = productNames,
                    productIcons = productIcons,
                    onDeviceDetail = onDeviceDetail,
                    onDeviceOperation = onDeviceOperation,
                    onServiceClick = onServiceClick
                )
            }
        }
    }
}

/**
 * 一台设备在树里的整棵子树：设备卡片本身 + （展开时）它依赖的服务、它的子设备。
 *
 * 顺序对齐 web 的设备页：**先服务行，后子设备**（`device.component.ts` 的 flattenDeviceRows）。
 * 子设备递归下去 —— 子设备自己也可能有子设备。
 *
 * 服务挂在它依赖的设备下（比对 did）。空间图里没有对应记录的服务不在这里出现，
 * 设备详情页那张卡走 /parent 接口，不受此限。
 */
@Composable
private fun DeviceSubtree(
    device: DeviceEntity,
    depth: Int,
    deviceChildren: Map<String, List<DeviceEntity>>,
    servicesByDid: Map<String, List<ModbusServiceBrief>>,
    expandedDeviceIds: Set<String>,
    onToggleDevice: (String) -> Unit,
    rootId: String,
    productNames: Map<String, String?>,
    productIcons: Map<String, String>,
    onDeviceDetail: ((String) -> Unit)?,
    onDeviceOperation: ((did: String, type: String, spaceId: String) -> Unit)?,
    onServiceClick: ((spaceId: String, serviceId: String) -> Unit)?
) {
    val did = device.did ?: return
    val children = deviceChildren[did].orEmpty()
    val ownServices = servicesByDid[did].orEmpty()
    // 「有子设备或有服务」才给箭头；没有的话留一段等宽空白，让同级设备的图标对齐
    val hasNested = children.isNotEmpty() || ownServices.isNotEmpty()
    val isExpanded = expandedDeviceIds.contains(did)

    val spaceId = device.space?.spaceId ?: rootId

    DeviceItem(
        device = device,
        onDetail = { onDeviceDetail?.invoke(did) },
        onOperation = { onDeviceOperation?.invoke(did, device.type ?: "", spaceId) },
        depth = depth,
        hasNested = hasNested,
        isExpanded = isExpanded,
        onToggle = { onToggleDevice(did) },
        productNames = productNames,
        productIcons = productIcons
    )

    if (!hasNested || !isExpanded) return

    ownServices.forEach { service ->
        ModbusServiceRow(service = service, depth = depth + 1) {
            service.id?.let { onServiceClick?.invoke(rootId, it) }
        }
    }
    children.forEach { child ->
        DeviceSubtree(
            device = child,
            depth = depth + 1,
            deviceChildren = deviceChildren,
            servicesByDid = servicesByDid,
            expandedDeviceIds = expandedDeviceIds,
            onToggleDevice = onToggleDevice,
            rootId = rootId,
            productNames = productNames,
            productIcons = productIcons,
            onDeviceDetail = onDeviceDetail,
            onDeviceOperation = onDeviceOperation,
            onServiceClick = onServiceClick
        )
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
    deviceIds: Set<String>,
    showActions: Boolean = true
) {
    // 与展开后实际铺出来的那批设备用**同一个过滤**（有父设备的挂到父设备下面去了），
    // 否则「本空间只有子设备、而父设备在别的空间」时，箭头点开会是空的。
    val spaceDevices = devices.filter {
        it.space?.spaceId == space.id && isDeviceTreeRoot(it, deviceIds)
    }
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
                    modifier = Modifier.size(20.dp).mirrorInRtl()
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
                    text = space.name ?: stringResource(R.string.common_unnamed),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpaceTypeChip(type = space.type ?: "other")
                    if (spaceDevices.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            // 数量传两次：一次选档位（英文 1 device / 2 devices），一次填 %1$d
                            text = pluralStringResource(
                                R.plurals.space_device_count,
                                spaceDevices.size, spaceDevices.size
                            ),
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
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.space_add_child), modifier = Modifier.size(18.dp))
                }
                if (depth > 0) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.common_delete),
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
    onDetail: (() -> Unit)? = null,
    onOperation: (() -> Unit)? = null,
    depth: Int = 0,
    /** 有子设备或有服务：卡片左边给一个展开箭头（与 [SpaceTreeNode] 同一个做法） */
    hasNested: Boolean = false,
    isExpanded: Boolean = false,
    onToggle: (() -> Unit)? = null,
    productNames: Map<String, String?> = emptyMap(),
    productIcons: Map<String, String> = emptyMap()
) {
    val model = extractModelFromUrn(device.type)
    val productIcon = model?.let { productIcons[it] }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (12 + depth * 20).dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            .then(if (onOperation != null) Modifier.clickable(onClick = onOperation) else Modifier),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            // 展开箭头：整张卡片点下去是「设备操作」，所以只有这一小块管展开，卡片本身不跟着切换
            // （与 [SpaceTreeNode] 不同，那边整卡都是展开）。
            // 触摸区对齐右边那颗「详情」chevron —— 44dp 宽 × 整卡高；小图标十几 dp，手指按不准。
            if (hasNested) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(44.dp)
                        .clickable { onToggle?.invoke() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = if (isExpanded) stringResource(R.string.common_collapse)
                            else stringResource(R.string.common_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).mirrorInRtl()
                    )
                }
            } else {
                Spacer(Modifier.width(44.dp))
            }
            // 左内边距让给上面的箭头槽了，右边距保持不变
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 0.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
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
                val dotColor = if (device.online == true) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.error
                Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(color = dotColor)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = productNames[extractModelFromUrn(device.type)]
                        ?: extractTypeName(device.type)
                        ?: device.type ?: device.did ?: stringResource(R.string.devices_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onDetail != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(44.dp)
                        .clickable(onClick = onDetail),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = stringResource(R.string.common_detail),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).mirrorInRtl()
                    )
                }
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
 * urn:<ns>:device:<name>:...  →  returns <name>
 */
private fun extractTypeName(urn: String?): String? {
    if (urn == null) return null
    val parts = urn.split(":")
    return if (parts.size >= 4) parts[3] else null
}

/**
 * Find a space by id in the space tree, searching recursively through children.
 */
private fun findSpaceById(root: SpaceEntity?, id: String): SpaceEntity? {
    if (root == null) return null
    if (root.id == id) return root
    return root.children?.firstNotNullOfOrNull { findSpaceById(it, id) }
}
