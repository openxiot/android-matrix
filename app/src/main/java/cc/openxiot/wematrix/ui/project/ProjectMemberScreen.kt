package cc.openxiot.wematrix.ui.project

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import cc.openxiot.wematrix.data.api.ProjectMember
import cc.openxiot.wematrix.ui.components.AvatarImage
import cc.openxiot.wematrix.ui.components.ConfirmDialog
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.ErrorMessage
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 项目（根空间）成员管理页，对齐 webapp ProjectMemberComponent：
 * - 管理员（isAdmin）可添加成员、调整角色、编辑备注、移除成员；
 * - 本人可退出项目（最后一个管理员 isLastAdmin 时不显示退出）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectMemberScreen(
    rootId: String,
    onBack: () -> Unit,
    viewModel: ProjectMemberViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(rootId) {
        viewModel.load(rootId)
    }

    // 退出项目成功后返回上一页
    LaunchedEffect(uiState.hasLeft) {
        if (uiState.hasLeft) onBack()
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
                        text = uiState.projectName ?: "项目成员",
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
            if (uiState.isAdmin) {
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
                    onRetry = { viewModel.load(rootId) }
                )
                uiState.members.isEmpty() -> EmptyState(
                    message = "暂无成员，点击右下角按钮添加"
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(uiState.members, key = { it.userId ?: it.name ?: it.hashCode() }) { member ->
                            ProjectMemberCard(
                                member = member,
                                isAdmin = uiState.isAdmin,
                                isLastAdmin = uiState.isLastAdmin,
                                currentUserId = uiState.currentUserId,
                                onChangeRole = { viewModel.showChangeRoleDialog(member) },
                                onEditRemark = { viewModel.showEditRemarkDialog(member) },
                                onRemove = { viewModel.showRemoveConfirm(member) },
                                onLeave = { viewModel.showLeaveConfirm() }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    // 添加成员 dialog（管理员）
    if (uiState.showAddDialog) {
        var memberId by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("member") }

        AlertDialog(
            onDismissRequest = { viewModel.hideAddMemberDialog() },
            title = { Text("添加成员") },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                Column {
                    OutlinedTextField(
                        value = memberId,
                        onValueChange = { memberId = it },
                        label = { Text("用户ID") },
                        placeholder = { Text("用户的账号 ID") },
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
                        viewModel.addMember(rootId, memberId.trim(), role)
                    },
                    enabled = memberId.isNotBlank()
                ) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideAddMemberDialog() }) { Text("取消") }
            }
        )
    }

    // 调整角色 dialog（管理员）
    uiState.changeRoleTarget?.let { member ->
        var newRole by remember(member.userId) { mutableStateOf(member.role) }
        AlertDialog(
            onDismissRequest = { viewModel.hideChangeRoleDialog() },
            title = { Text("调整角色 - ${member.name ?: member.userId}") },
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
                    onClick = { viewModel.updateRole(rootId, member, newRole ?: "member") },
                    enabled = newRole != member.role
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideChangeRoleDialog() }) { Text("取消") }
            }
        )
    }

    // 编辑备注 dialog（管理员）
    uiState.editRemarkTarget?.let { member ->
        var remark by remember(member.userId) { mutableStateOf(member.remark.orEmpty()) }
        AlertDialog(
            onDismissRequest = { viewModel.hideEditRemarkDialog() },
            title = { Text("编辑备注 - ${member.name ?: member.userId}") },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    placeholder = { Text("填写备注") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.updateRemark(rootId, member, remark) }
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideEditRemarkDialog() }) { Text("取消") }
            }
        )
    }

    // 移除成员确认（管理员）
    uiState.removeTarget?.let { member ->
        ConfirmDialog(
            title = "移除成员",
            message = "确定要移除「${member.name ?: member.userId}」吗？",
            onConfirm = { member.userId?.let { viewModel.removeMember(rootId, it) } },
            onDismiss = { viewModel.hideRemoveConfirm() }
        )
    }

    // 退出项目确认（本人）
    if (uiState.showLeaveConfirm) {
        ConfirmDialog(
            title = "退出项目",
            message = "确定要退出这个项目吗？退出后将无法访问该项目。",
            onConfirm = { viewModel.leaveProject(rootId) },
            onDismiss = { viewModel.hideLeaveConfirm() }
        )
    }
}

@Composable
private fun ProjectMemberCard(
    member: ProjectMember,
    isAdmin: Boolean,
    isLastAdmin: Boolean,
    currentUserId: String?,
    onChangeRole: () -> Unit,
    onEditRemark: () -> Unit,
    onRemove: () -> Unit,
    onLeave: () -> Unit
) {
    val isSelf = member.userId != null && member.userId == currentUserId
    val canManage = isAdmin && !isSelf
    val canLeave = isSelf && !isLastAdmin
    val hasActions = canManage || canLeave

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
        // 左提示（右滑露出）：管理员=移除成员，本人且非最后管理员=退出项目
        if (canManage || canLeave) {
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
                        if (canManage) "移除成员" else "退出项目",
                        color = if (isRightSwipe && isPastTwoThirds) Color.White
                                else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 右提示（左滑露出）：管理员调整角色
        if (canManage) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
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
                        "调整角色",
                        color = if (!isRightSwipe && isPastTwoThirds) Color.White
                                else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 前景卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .then(
                    if (hasActions) {
                        Modifier.pointerInput(canManage, canLeave) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (abs(offsetX.value) > maxOffsetPx * 2f / 3f) {
                                            if (offsetX.value >= 0f) {
                                                when {
                                                    canManage -> onRemove()
                                                    canLeave -> onLeave()
                                                }
                                            } else if (canManage) {
                                                onChangeRole()
                                            }
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
                                text = member.name ?: member.userId ?: "未命名",
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
                            text = member.userId ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // 备注：管理员可点击编辑
                        if (canManage) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable(onClick = onEditRemark)
                            ) {
                                Text(
                                    text = if (member.remark.isNullOrBlank()) "点击添加备注"
                                           else member.remark,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "编辑备注",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else if (!member.remark.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = member.remark,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    ProjectRoleBadge(role = member.role)
                }
            }
        }
    }

    // 操作权限变化时重置滑动
    LaunchedEffect(isAdmin, isSelf, isLastAdmin) {
        offsetX.animateTo(0f)
    }
}

@Composable
private fun ProjectRoleBadge(role: String?) {
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
