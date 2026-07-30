package cc.openxiot.wematrix.ui.organization

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.data.api.Member
import cc.openxiot.wematrix.data.api.Organization
import cc.openxiot.wematrix.ui.components.AvatarImage
import cc.openxiot.wematrix.ui.components.ConfirmDialog
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.ErrorMessage
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationDetailScreen(
    orgId: String,
    onBack: () -> Unit,
    viewModel: OrgDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(orgId) {
        viewModel.loadOrganization(orgId)
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
                        text = uiState.organization?.name ?: "组织详情",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        floatingActionButton = {
            if (uiState.isCurrentUserAdmin) {
                FloatingActionButton(
                    onClick = { viewModel.showAddMemberDialog() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "添加成员")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                uiState.isLoading -> LoadingIndicator()
                uiState.error != null -> ErrorMessage(
                    message = uiState.error!!,
                    onRetry = { viewModel.loadOrganization(orgId) }
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(uiState.members, key = { it.developerId }) { member ->
                            MemberCard(
                                member = member,
                                isAdmin = uiState.isCurrentUserAdmin,
                                isSelf = member.developerId == uiState.currentUserId,
                                onChangeRole = { viewModel.showChangeRoleDialog(member) },
                                onRemove = { viewModel.showRemoveConfirm(member) }
                            )
                        }

                        if (uiState.members.isEmpty() && !uiState.isLoading) {
                            item {
                                EmptyState(message = "暂无成员")
                            }
                        }
                    }
                }
            }
        }
    }

    // Add member dialog
    if (uiState.showAddDialog) {
        var developerId by remember { mutableStateOf("") }
        var memberName by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("member") }

        AlertDialog(
            onDismissRequest = { viewModel.hideAddMemberDialog() },
            title = { Text("添加成员") },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                Column {
                    OutlinedTextField(
                        value = developerId,
                        onValueChange = { developerId = it },
                        label = { Text("开发者ID") },
                        placeholder = { Text("用户的 developerId") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = memberName,
                        onValueChange = { memberName = it },
                        label = { Text("显示名称") },
                        placeholder = { Text("可选") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("角色", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = role == "member",
                            onClick = { role = "member" }
                        )
                        Text("普通成员", modifier = Modifier.clickable { role = "member" })
                        Spacer(modifier = Modifier.width(24.dp))
                        RadioButton(
                            selected = role == "admin",
                            onClick = { role = "admin" }
                        )
                        Text("管理员", modifier = Modifier.clickable { role = "admin" })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addMember(
                            orgId = orgId,
                            developerId = developerId,
                            name = memberName.ifBlank { developerId },
                            role = role
                        )
                    },
                    enabled = developerId.isNotBlank()
                ) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideAddMemberDialog() }) { Text("取消") }
            }
        )
    }

    // Change role dialog
    uiState.changeRoleTarget?.let { member ->
        var newRole by remember(member.developerId) { mutableStateOf(member.role) }
        AlertDialog(
            onDismissRequest = { viewModel.hideChangeRoleDialog() },
            title = { Text("更改角色 - ${member.name}") },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(
                            selected = newRole == "member",
                            onClick = { newRole = "member" }
                        )
                        Text("普通成员", modifier = Modifier.clickable { newRole = "member" })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(
                            selected = newRole == "admin",
                            onClick = { newRole = "admin" }
                        )
                        Text("管理员", modifier = Modifier.clickable { newRole = "admin" })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateMemberRole(orgId, member.developerId, memberName = member.name, newRole = newRole)
                    },
                    enabled = newRole != member.role
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideChangeRoleDialog() }) { Text("取消") }
            }
        )
    }

    // Remove confirm dialog
    uiState.removeTarget?.let { member ->
        ConfirmDialog(
            title = "移除成员",
            message = "确定要移除「${member.name}」吗？",
            onConfirm = { viewModel.removeMember(orgId, member.developerId) },
            onDismiss = { viewModel.hideRemoveConfirm() }
        )
    }
}

@Composable
private fun MemberCard(
    member: Member,
    isAdmin: Boolean,
    isSelf: Boolean,
    onChangeRole: () -> Unit,
    onRemove: () -> Unit
) {
    val canInteract = isAdmin && !isSelf
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxOffset = 180.dp
    val maxOffsetPx = with(density) { maxOffset.toPx() }
    val offsetX = remember { Animatable(0f) }
    val isRightSwipe by remember { derivedStateOf { offsetX.value >= 0f } }
    val isPastTwoThirds by remember { derivedStateOf { abs(offsetX.value) > maxOffsetPx * 2f / 3f } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clipToBounds()
    ) {
        // Background 两侧提示
        Row(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
        ) {
            // 左：右滑移除成员（右滑时露出）
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
                        "移除成员",
                        color = if (isRightSwipe && isPastTwoThirds) Color.White
                                else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 中：占位
            Spacer(Modifier.weight(1f))

            // 右：左滑更改角色（左滑时露出）
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
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = if (!isRightSwipe && isPastTwoThirds) Color.White
                               else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "更改角色",
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
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(onChangeRole) {
                    detectTapGestures {
                        if (canInteract) onChangeRole()
                    }
                }
                .then(
                    if (canInteract) {
                        Modifier.pointerInput(onChangeRole, onRemove) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (abs(offsetX.value) > maxOffsetPx * 2f / 3f) {
                                            if (offsetX.value >= 0f) onRemove() else onChangeRole()
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
                        }
                    } else Modifier
                ),
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
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarImage(
                        url = null,
                        name = member.name,
                        size = 40.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = member.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isSelf) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "我",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = member.developerId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    RoleBadge(role = member.role)
                }
            }
        }
    }

    // 选中状态改变时重置滑动
    LaunchedEffect(isAdmin, isSelf) {
        offsetX.animateTo(0f)
    }
}

@Composable
private fun RoleBadge(role: String) {
    val (label, containerColor, contentColor) = when (role) {
        "admin" -> Triple("管理员", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        else -> Triple("成员", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}
