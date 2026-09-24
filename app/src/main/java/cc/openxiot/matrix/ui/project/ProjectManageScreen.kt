package cc.openxiot.matrix.ui.project

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.matrix.R
import cc.openxiot.matrix.ui.components.ConfirmDialog
import cc.openxiot.matrix.ui.components.EmptyState
import cc.openxiot.matrix.ui.components.ErrorMessage
import cc.openxiot.matrix.ui.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectManageScreen(
    onBack: () -> Unit,
    onEditProject: (String) -> Unit,
    viewModel: ProjectViewModel = viewModel()
) {
    val state by viewModel.projectState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadRootSpaces()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.project_manage_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.project_create_title))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                state.isLoading -> LoadingIndicator()
                state.error != null -> ErrorMessage(
                    message = state.error!!,
                    onRetry = { viewModel.loadRootSpaces() }
                )
                state.rootSpaces.isEmpty() -> EmptyState(message = stringResource(R.string.project_empty))
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(state.rootSpaces, key = { it.id ?: it.name ?: "" }) { space ->
                            ProjectManageCard(
                                name = space.name ?: stringResource(R.string.common_unnamed),
                                type = space.type,
                                isSelected = space.id != null && space.id == state.currentRootId,
                                onSelect = { space.id?.let { onEditProject(it) } },
                                onRename = { space.id?.let { id -> renameTarget = id to (space.name ?: "") } },
                                onDelete = { space.id?.let { showDeleteConfirm = it } }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    // Create dialog
    if (showCreateDialog) {
        var projectName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.project_create_title)) },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text(stringResource(R.string.project_name_label)) },
                    placeholder = { Text(stringResource(R.string.project_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createSpace(
                            name = projectName,
                            type = "site",
                            parentId = null,
                            rootId = null
                        )
                        showCreateDialog = false
                    },
                    enabled = projectName.isNotBlank()
                ) {
                    Text(stringResource(R.string.common_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Rename dialog
    renameTarget?.let { (spaceId, currentName) ->
        var newName by remember { mutableStateOf(currentName) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.project_rename_title)) },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.project_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameRootSpace(spaceId, newName)
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

    // Delete confirm
    showDeleteConfirm?.let { spaceId ->
        ConfirmDialog(
            title = stringResource(R.string.project_delete_title),
            message = stringResource(R.string.project_delete_confirm),
            onConfirm = {
                viewModel.deleteSpace(spaceId, null)
                showDeleteConfirm = null
            },
            onDismiss = { showDeleteConfirm = null }
        )
    }
}

@Composable
private fun ProjectManageCard(
    name: String,
    type: String?,
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
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Business,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (type != null) {
                    Text(
                        text = stringResource(R.string.project_type_label, type),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.project_rename_action))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
