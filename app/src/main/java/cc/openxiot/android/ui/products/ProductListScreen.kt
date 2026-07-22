package cc.openxiot.android.ui.products

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.android.data.api.ProductEntity
import cc.openxiot.android.ui.components.EmptyState
import cc.openxiot.android.ui.components.ErrorMessage
import cc.openxiot.android.ui.components.LoadingIndicator
import cc.openxiot.android.ui.main.PageTitle
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    viewModel: ProductViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            isRefreshing = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle(title = "产品")

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.loadProducts()
            },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
            state.isLoading -> LoadingIndicator()
            state.error != null -> ErrorMessage(
                message = state.error!!,
                onRetry = { viewModel.loadProducts() }
            )
            state.products.isEmpty() -> EmptyState(message = "暂无产品")
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item {
                        Text(
                            text = "共 ${state.products.size} 个产品",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    items(state.products, key = { it.id ?: it.model ?: "" }) { product ->
                        ProductCard(product = product)
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
        }
    }
}

@Composable
private fun ProductCard(product: ProductEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                if (product.icon != null) {
                    AsyncImage(
                        model = product.icon,
                        contentDescription = product.displayName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.DeviceHub,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "型号: ${product.model ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (product.protocol != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = product.protocol,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                LifecycleBadge(lifecycle = product.lifecycle)
            }
        }
    }
}

@Composable
private fun LifecycleBadge(lifecycle: String?) {
    if (lifecycle == null) return
    val (label, color) = when (lifecycle) {
        "released" -> "已发布" to MaterialTheme.colorScheme.primary
        "preview" -> "预览版" to MaterialTheme.colorScheme.tertiary
        "development" -> "开发中" to MaterialTheme.colorScheme.error
        else -> lifecycle to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}
