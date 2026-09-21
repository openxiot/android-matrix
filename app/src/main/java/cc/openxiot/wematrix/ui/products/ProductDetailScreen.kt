package cc.openxiot.wematrix.ui.products

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.R
import coil.compose.AsyncImage
import cc.openxiot.wematrix.data.api.ProductEntity
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import cc.openxiot.wematrix.ui.components.ErrorMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    productId: String,
    onBack: () -> Unit,
    viewModel: ProductViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val product = state.products.find { it.id == productId }
    val context = LocalContext.current

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    Text(
                        text = product?.displayName ?: stringResource(
                            if (product == null) R.string.product_detail_title
                            else R.string.common_unknown_product
                        ),
                        style = MaterialTheme.typography.titleMedium,
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
                    Text(stringResource(R.string.product_not_found), style = MaterialTheme.typography.bodyLarge)
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
                    HeaderCard(product = product)
                    DetailCard(product = product)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { openJdApp(context) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE4393C)
                            )
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.product_buy_jd), style = MaterialTheme.typography.titleSmall)
                        }
                        Button(
                            onClick = { openTaobaoApp(context) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF6A00)
                            )
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.product_buy_taobao), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

private fun openJdApp(context: Context) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage("com.jingdong.app.mall")
        if (intent != null) {
            context.startActivity(intent)
        } else {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.jd.com")))
        }
    } catch (_: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.jd.com")))
    }
}

private fun openTaobaoApp(context: Context) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage("com.taobao.taobao")
        if (intent != null) {
            context.startActivity(intent)
        } else {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.taobao.com")))
        }
    } catch (_: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.taobao.com")))
    }
}

@Composable
private fun HeaderCard(product: ProductEntity) {
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
                        contentDescription = product.displayName ?: stringResource(R.string.common_unknown_product),
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
                    text = product.displayName ?: stringResource(R.string.common_unknown_product),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                LifecycleBadge(lifecycle = product.lifecycle)
            }
        }
    }
}

@Composable
private fun DetailCard(product: ProductEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            DetailRow(label = stringResource(R.string.product_id_label), value = product.id ?: "-")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailRow(label = stringResource(R.string.product_model_label), value = product.model ?: "-")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailRow(label = stringResource(R.string.common_protocol_label), value = product.protocol ?: "-")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            if (product.organization != null) {
                DetailRow(label = stringResource(R.string.product_org_label), value = product.organization)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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

@Composable
private fun LifecycleBadge(lifecycle: String?) {
    if (lifecycle == null) return
    val (label, color) = when (lifecycle) {
        "released" -> stringResource(R.string.product_status_released) to MaterialTheme.colorScheme.primary
        "preview" -> stringResource(R.string.product_status_preview) to MaterialTheme.colorScheme.tertiary
        "development" -> stringResource(R.string.product_status_development) to MaterialTheme.colorScheme.error
        else -> lifecycle to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}
