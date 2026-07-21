package cc.openxiot.android.ui.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.android.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectPickerScreen(
    onBack: () -> Unit,
    onManage: () -> Unit,
    viewModel: ProjectViewModel = viewModel()
) {
    val state by viewModel.projectState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadRootSpaces()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("项目", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onManage) {
                        Icon(Icons.Default.Settings, contentDescription = "管理")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                state.isLoading -> LoadingIndicator()
                state.error != null -> ErrorMessage(
                    message = state.error!!,
                    onRetry = { viewModel.loadRootSpaces() }
                )
                state.rootSpaces.isEmpty() -> EmptyState(message = "暂无项目")
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(state.rootSpaces, key = { it.id ?: it.name ?: "" }) { space ->
                            ProjectPickerCard(
                                name = space.name ?: "未命名",
                                type = space.type,
                                isSelected = space.id != null && space.id == state.currentRootId,
                                onClick = {
                                    viewModel.selectRootSpace(space)
                                    onBack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectPickerCard(
    name: String,
    type: String?,
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
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                        text = "类型: ${type}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
