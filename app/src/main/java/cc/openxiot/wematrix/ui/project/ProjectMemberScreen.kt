package cc.openxiot.wematrix.ui.project

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.R
import cc.openxiot.wematrix.data.api.ProjectMember
import cc.openxiot.wematrix.ui.components.AvatarImage
import cc.openxiot.wematrix.ui.components.ConfirmDialog
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.ErrorMessage
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import cc.openxiot.wematrix.util.dirSign
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    Text(
                        text = uiState.projectName ?: stringResource(R.string.project_members_title),
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
                    Icon(Icons.Default.PersonAdd, contentDescription = stringResource(R.string.common_member_add))
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
                    message = stringResource(R.string.project_members_empty)
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
            title = { Text(stringResource(R.string.common_member_add)) },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                Column {
                    OutlinedTextField(
                        value = memberId,
                        onValueChange = { memberId = it },
                        label = { Text(stringResource(R.string.project_user_id)) },
                        placeholder = { Text(stringResource(R.string.project_user_id_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.common_role_label), style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = role == "member",
                            onClick = { role = "member" }
                        )
                        Text(stringResource(R.string.common_role_member_option), modifier = Modifier.clickable { role = "member" })
                        Spacer(modifier = Modifier.width(24.dp))
                        RadioButton(
                            selected = role == "admin",
                            onClick = { role = "admin" }
                        )
                        Text(stringResource(R.string.common_role_admin), modifier = Modifier.clickable { role = "admin" })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addMember(rootId, memberId.trim(), role)
                    },
                    enabled = memberId.isNotBlank()
                ) { Text(stringResource(R.string.common_add)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideAddMemberDialog() }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // 调整角色 dialog（管理员）
    uiState.changeRoleTarget?.let { member ->
        var newRole by remember(member.userId) { mutableStateOf(member.role) }
        AlertDialog(
            onDismissRequest = { viewModel.hideChangeRoleDialog() },
            title = { Text(stringResource(R.string.project_role_adjust_title, member.name ?: member.userId ?: stringResource(R.string.common_unnamed))) },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(
                            selected = newRole == "member",
                            onClick = { newRole = "member" }
                        )
                        Text(stringResource(R.string.common_role_member_option), modifier = Modifier.clickable { newRole = "member" })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(
                            selected = newRole == "admin",
                            onClick = { newRole = "admin" }
                        )
                        Text(stringResource(R.string.common_role_admin), modifier = Modifier.clickable { newRole = "admin" })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.updateRole(rootId, member, newRole ?: "member") },
                    enabled = newRole != member.role
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideChangeRoleDialog() }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // 编辑备注 dialog（管理员）
    uiState.editRemarkTarget?.let { member ->
        var remark by remember(member.userId) { mutableStateOf(member.remark.orEmpty()) }
        AlertDialog(
            onDismissRequest = { viewModel.hideEditRemarkDialog() },
            title = { Text(stringResource(R.string.project_remark_title, member.name ?: member.userId ?: stringResource(R.string.common_unnamed))) },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    placeholder = { Text(stringResource(R.string.project_remark_label)) },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.updateRemark(rootId, member, remark) }
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideEditRemarkDialog() }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // 移除成员确认（管理员）
    uiState.removeTarget?.let { member ->
        ConfirmDialog(
            title = stringResource(R.string.common_member_remove),
            message = stringResource(R.string.project_remove_confirm, member.name ?: member.userId ?: stringResource(R.string.common_unnamed)),
            onConfirm = { member.userId?.let { viewModel.removeMember(rootId, it) } },
            onDismiss = { viewModel.hideRemoveConfirm() }
        )
    }

    // 退出项目确认（本人）
    if (uiState.showLeaveConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.project_exit),
            message = stringResource(R.string.project_exit_confirm),
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
                        if (canManage) stringResource(R.string.common_member_remove)
                        else stringResource(R.string.project_exit),
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
                        stringResource(R.string.project_role_adjust),
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
                .absoluteOffset { IntOffset(offsetX.value.roundToInt(), 0) }
                .then(
                    if (hasActions) {
                        Modifier.pointerInput(canManage, canLeave, dirSign) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (abs(offsetX.value) > maxOffsetPx * 2f / 3f) {
                                            // 按语义方向判：RTL 下往左拖才是「移除」
                                            if (offsetX.value * dirSign >= 0f) {
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
                )
                // 管理员点击成员卡片 → 编辑备注（点击与左右滑动手势共存）
                .then(
                    if (canManage) Modifier.clickable(onClick = onEditRemark) else Modifier
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
                                text = member.name ?: member.userId ?: stringResource(R.string.common_unnamed),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isSelf) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.common_me),
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
                        // 备注：管理员点击整个成员卡片即可编辑
                        if (canManage || !member.remark.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (member.remark.isNullOrBlank()) stringResource(R.string.project_remark_hint)
                                       else member.remark,
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
        "admin" -> Triple(stringResource(R.string.common_role_admin), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        else -> Triple(stringResource(R.string.common_role_member), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
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
