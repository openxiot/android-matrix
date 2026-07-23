package cc.openxiot.android.ui.products

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import cc.openxiot.android.data.api.ProductEntity
import cc.openxiot.android.ui.components.LoadingIndicator
import cc.openxiot.android.ui.components.ErrorMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    productId: String,
    onBack: () -> Unit,
    viewModel: ProductViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val product = state.products.find { it.id == productId }

    LaunchedEffect(productId) {
        viewModel.loadProductDetail(productId)
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
                        text = product?.displayName ?: "产品详情",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        when {
            state.isDetailLoading && product == null -> LoadingIndicator(modifier = Modifier.fillMaxSize())
            state.detailError != null -> ErrorMessage(
                message = state.detailError!!,
                onRetry = { viewModel.loadProductDetail(productId) },
                modifier = Modifier.fillMaxSize()
            )
            product == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("产品未找到", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(56.dp),
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
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = product.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                LifecycleBadge(lifecycle = product.lifecycle)
                            }
                        }
                    }

                    // Detail info
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            DetailRow(label = "产品 ID", value = product.id ?: "-")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            DetailRow(label = "产品型号", value = product.model ?: "-")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            DetailRow(label = "通信协议", value = product.protocol ?: "-")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            if (product.organization != null) {
                                DetailRow(label = "所属组织", value = product.organization)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                            if (product.template != null) {
                                DetailRow(label = "模板", value = product.template)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
