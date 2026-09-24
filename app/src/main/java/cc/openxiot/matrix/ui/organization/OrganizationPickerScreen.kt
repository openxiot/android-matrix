package cc.openxiot.matrix.ui.organization

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.api.Organization
import cc.openxiot.matrix.util.dirSign
import cc.openxiot.matrix.ui.components.ConfirmDialog
import cc.openxiot.matrix.ui.components.EmptyState
import cc.openxiot.matrix.ui.components.ErrorMessage
import cc.openxiot.matrix.ui.components.LoadingIndicator
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationPickerScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit = {},
    viewModel: OrganizationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<Organization?>(null) }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) {
            isRefreshing = false
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    Text(
                        text = stringResource(R.string.org_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.org_create_title))
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.loadOrganizations()
            },
            modifier = Modifier.padding(padding)
        ) {
            when {
                uiState.isLoading -> LoadingIndicator()
                uiState.error != null -> ErrorMessage(
                    message = uiState.error!!,
                    onRetry = { viewModel.loadOrganizations() }
                )
                uiState.organizations.isEmpty() -> EmptyState(
                    message = stringResource(R.string.org_empty)
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(uiState.organizations, key = { it.id ?: it.name ?: "" }) { org ->
                            OrgSwipeCard(
                                organization = org,
                                isSelected = org.id != null && org.id == uiState.currentOrgId,
                                onSelect = {
                                    viewModel.selectOrganization(org)
                                    onBack()
                                },
                                onNavigateToDetail = { org.id?.let(onNavigateToDetail) },
                                onDelete = { showDeleteConfirm = org.id },
                                onRename = { renameTarget = org }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    // Create dialog
    if (uiState.showCreateDialog) {
        var orgId by remember { mutableStateOf("") }
        var orgName by remember { mutableStateOf("") }
        val orgCodeRegex = Regex("^[a-zA-Z][a-zA-Z0-9-]*$")
        val orgCodeValid = orgId.isBlank() || orgCodeRegex.matches(orgId)
        AlertDialog(
            onDismissRequest = { viewModel.hideCreateDialog() },
            title = { Text(stringResource(R.string.org_create_title)) },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                Column {
                    OutlinedTextField(
                        value = orgId,
                        onValueChange = { orgId = it },
                        label = { Text(stringResource(R.string.org_id_label)) },
                        placeholder = { Text(stringResource(R.string.org_id_placeholder)) },
                        singleLine = true,
                        isError = orgId.isNotBlank() && !orgCodeValid,
                        supportingText = if (orgId.isNotBlank() && !orgCodeValid) {
                            { Text(stringResource(R.string.org_id_hint)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = orgName,
                        onValueChange = { orgName = it },
                        label = { Text(stringResource(R.string.org_name_label)) },
                        placeholder = { Text(stringResource(R.string.org_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (orgId.isNotBlank() && orgName.isNotBlank() && orgCodeValid) {
                            viewModel.createOrganization(orgId, orgName)
                        }
                    },
                    enabled = orgId.isNotBlank() && orgName.isNotBlank() && orgCodeValid
                ) {
                    Text(stringResource(R.string.common_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideCreateDialog() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Delete confirm dialog
    showDeleteConfirm?.let { orgId ->
        ConfirmDialog(
            title = stringResource(R.string.org_delete_title),
            message = stringResource(R.string.org_delete_confirm),
            onConfirm = {
                viewModel.deleteOrganization(orgId)
                showDeleteConfirm = null
            },
            onDismiss = { showDeleteConfirm = null }
        )
    }

    // Rename dialog
    renameTarget?.let { org ->
        var newName by remember { mutableStateOf(org.name ?: "") }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.org_rename_title)) },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.org_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        org.id?.let { viewModel.renameOrganization(it, newName) }
                        renameTarget = null
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun OrgSwipeCard(
    organization: Organization,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onNavigateToDetail: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxOffset = 180.dp
    val maxOffsetPx = with(density) { maxOffset.toPx() }
    val offsetX = remember { Animatable(0f) }
    // offsetX 与 dragAmount 都是**物理像素**。卡片刻意用 `absoluteOffset`（rtlAware=false、
    // 落在物理 x），**不能**用 `offset` —— 后者是 rtlAware 的（Compose 里走
    // `placeRelativeWithLayer`，RTL 下落到 `parentWidth - width - x`），拿它配物理 dragAmount
    // 会让阿拉伯语下卡片朝手指的**反方向**滑。背景 Row 相反：它**要**镜像（露出哪一侧跟着
    // 拖动方向走），所以判定乘方向符号与之对齐，否则「往左拖露出删除、执行的却是改名」。
    // LTR 下 absoluteOffset 与 offset 同义、dirSign = 1f，像素与算式逐字不变。
    val dirSign = dirSign(LocalLayoutDirection.current)
    val isRightSwipe by remember(dirSign) { derivedStateOf { offsetX.value * dirSign >= 0f } }
    val isPastTwoThirds by remember { derivedStateOf { abs(offsetX.value) > maxOffsetPx * 2f / 3f } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clipToBounds()
    ) {
        // Background 两侧提示。RTL 下这个 Row 会**自动镜像**（删除跑到右边），
        // 这是想要的：露出哪一侧始终跟着拖动方向走。判定端靠 dirSign 与之对齐。
        Row(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
        ) {
            // 左：右滑删除（右滑时露出）
            Box(
                modifier = Modifier
                    .width(maxOffset)
                    .fillMaxHeight()
                    .background(
                        if (isRightSwipe && isPastTwoThirds) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 20.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = if (isRightSwipe && isPastTwoThirds) Color.White
                               else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.org_swipe_delete),
                        color = if (isRightSwipe && isPastTwoThirds) Color.White
                                else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 中：占位
            Spacer(Modifier.weight(1f))

            // 右：左滑重命名（左滑时露出）
            Box(
                modifier = Modifier
                    .width(maxOffset)
                    .fillMaxHeight()
                    .background(
                        if (!isRightSwipe && isPastTwoThirds) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 20.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = if (!isRightSwipe && isPastTwoThirds) Color.White
                               else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.common_rename_action),
                        color = if (!isRightSwipe && isPastTwoThirds) Color.White
                                else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Foreground 卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .absoluteOffset { IntOffset(offsetX.value.roundToInt(), 0) }
                // Tap：点击内容区域选择，点击箭头进入详情
                .pointerInput(onSelect) {
                    detectTapGestures { onSelect() }
                }
                // Drag：水平双向拖动，松手后判定
                .pointerInput(onDelete, onRename, dirSign) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (abs(offsetX.value) > maxOffsetPx * 2f / 3f) {
                                    // 同样按语义方向判：RTL 下往左拖才是「删除」
                                    if (offsetX.value * dirSign >= 0f) onDelete() else onRename()
                                }
                                offsetX.animateTo(0f)
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo(
                                    (offsetX.value + dragAmount)
                                        .coerceIn(-maxOffsetPx, maxOffsetPx)
                                )
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = organization.name ?: organization.id ?: "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        val memberCount = organization.members?.size ?: 0
                        if (memberCount > 0) {
                            Text(
                                // 数量传两次：一次选档位（英文 1 member / 2 members），一次填 %1$d
                                text = pluralStringResource(
                                    R.plurals.org_member_count,
                                    memberCount, memberCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(64.dp)
                        .clickable(onClick = onNavigateToDetail),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.org_manage),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // 选中状态改变时重置滑动
    LaunchedEffect(isSelected) {
        offsetX.animateTo(0f)
    }
}
