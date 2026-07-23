package cc.openxiot.wematrix.ui.organization

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.data.api.Organization
import cc.openxiot.wematrix.ui.components.AvatarImage
import cc.openxiot.wematrix.ui.components.ConfirmDialog
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.ErrorMessage
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import cc.openxiot.wematrix.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationListScreen(
    onBack: () -> Unit,
    viewModel: OrganizationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<Organization?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("组织管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "创建组织")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                uiState.isLoading -> LoadingIndicator()
                uiState.error != null -> ErrorMessage(
                    message = uiState.error!!,
                    onRetry = { viewModel.loadOrganizations() }
                )
                uiState.organizations.isEmpty() -> EmptyState(message = "还没有组织，点击右下角按钮创建")
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        item { SectionHeader("我的组织") }
                        items(uiState.organizations, key = { it.id ?: it.name ?: "" }) { org ->
                            OrgManageCard(
                                organization = org,
                                isSelected = org.id != null && org.id == uiState.currentOrgId,
                                onSelect = { viewModel.selectOrganization(org) },
                                onRename = { renameTarget = org },
                                onDelete = { showDeleteConfirm = org.id }
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
            title = { Text("创建组织") },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                Column {
                    OutlinedTextField(
                        value = orgId,
                        onValueChange = { orgId = it },
                        label = { Text("组织标识") },
                        placeholder = { Text("如: my-company") },
                        singleLine = true,
                        isError = orgId.isNotBlank() && !orgCodeValid,
                        supportingText = if (orgId.isNotBlank() && !orgCodeValid) {
                            { Text("以英文开头，只能包含英文、数字和中划线") }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = orgName,
                        onValueChange = { orgName = it },
                        label = { Text("组织名称") },
                        placeholder = { Text("如: 我的公司") },
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
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideCreateDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    // Rename dialog
    renameTarget?.let { org ->
        var newName by remember { mutableStateOf(org.name ?: "") }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名组织") },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("组织名称") },
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
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("取消")
                }
            }
        )
    }

    // Delete confirm dialog
    showDeleteConfirm?.let { orgId ->
        ConfirmDialog(
            title = "删除组织",
            message = "确定要删除这个组织吗？",
            onConfirm = {
                viewModel.deleteOrganization(orgId)
                showDeleteConfirm = null
            },
            onDismiss = { showDeleteConfirm = null }
        )
    }
}

@Composable
private fun OrgManageCard(
    organization: Organization,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onSelect),
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
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarImage(url = null, name = organization.name, size = 44.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = organization.name ?: organization.id ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                val memberCount = organization.members?.size ?: 0
                if (memberCount > 0) {
                    Text(
                        text = "${memberCount} 位成员",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "重命名")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
