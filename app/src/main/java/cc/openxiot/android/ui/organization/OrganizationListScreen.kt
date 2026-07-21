package cc.openxiot.android.ui.organization

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.android.AppState
import cc.openxiot.android.OpenXiotApp
import cc.openxiot.android.data.api.Organization
import cc.openxiot.android.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationListScreen(
    onOrgSelected: (Organization) -> Unit,
    onLogout: () -> Unit,
    viewModel: OrganizationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tokenManager = OpenXiotApp.instance.tokenManager

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "智能场地",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.currentOrgName != null) {
                            Text(
                                text = "当前组织: ${uiState.currentOrgName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        AppState.toggleDarkMode(tokenManager)
                    }) {
                        Icon(
                            if (AppState.isDarkMode) Icons.Default.LightMode
                            else Icons.Default.DarkMode,
                            contentDescription = "切换主题"
                        )
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "退出登录")
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
                        items(uiState.organizations) { org ->
                            OrganizationCard(
                                organization = org,
                                isSelected = org.id == uiState.currentOrgId,
                                onClick = {
                                    viewModel.selectOrganization(org)
                                    onOrgSelected(org)
                                }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    if (uiState.showCreateDialog) {
        var orgId by remember { mutableStateOf("") }
        var orgName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.hideCreateDialog() },
            title = { Text("创建组织") },
            text = {
                Column {
                    OutlinedTextField(
                        value = orgId,
                        onValueChange = { orgId = it },
                        label = { Text("组织标识") },
                        placeholder = { Text("如: my-company") },
                        singleLine = true,
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
                        if (orgId.isNotBlank() && orgName.isNotBlank()) {
                            viewModel.createOrganization(orgId, orgName)
                        }
                    },
                    enabled = orgId.isNotBlank() && orgName.isNotBlank()
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
}

@Composable
private fun OrganizationCard(
    organization: Organization,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 2.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarImage(
                url = null,
                name = organization.name,
                size = 44.dp
            )
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
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
