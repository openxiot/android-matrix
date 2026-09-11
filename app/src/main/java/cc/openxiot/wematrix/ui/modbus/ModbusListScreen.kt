package cc.openxiot.wematrix.ui.modbus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.data.api.ModbusConfig
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.ErrorMessage
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import cc.openxiot.wematrix.ui.theme.Blue500
import cc.openxiot.wematrix.ui.theme.Gray500
import cc.openxiot.wematrix.ui.theme.Green
import cc.openxiot.wematrix.ui.theme.Orange

/**
 * 设备点表列表（只读）。
 *
 * web 侧是一张 10 列宽表（`modbus.component.html`），手机上照搬没法用 —— 同一批字段改映射到卡片：
 * 厂家+型号当标题，从站地址/可见度/状态当一行标签，描述与创建者、更新时间压到底部小字。
 *
 * 与 web 的一处有意差异：不显示「所属组织」列（那要把 orgId 再查一次换成组织名，本端没有这份数据）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModbusListScreen(
    onBack: () -> Unit,
    onConfigClick: (String) -> Unit,
    viewModel: ModbusViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

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
                        text = "设备点表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading && state.configs.isEmpty() -> LoadingIndicator()

                state.error != null && state.configs.isEmpty() -> ErrorMessage(
                    message = state.error!!,
                    onRetry = { viewModel.loadConfigs() }
                )

                state.configs.isEmpty() -> EmptyState("暂无设备点表")

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
                ) {
                    items(state.configs) { config ->
                        ModbusConfigCard(config = config) {
                            config.id?.let(onConfigClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModbusConfigCard(config: ModbusConfig, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "从站地址 ${config.slave?.slaveId ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    InfoChip(visibilityLabel(config.visibility), visibilityColor(config.visibility))
                    Spacer(Modifier.width(6.dp))
                    InfoChip(lifecycleLabel(config.lifecycle), lifecycleColor(config.lifecycle))
                }

                val description = config.slave?.description
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "创建者 ${config.creator?.name ?: "-"} · 更新 ${
                        formatEpochMillis(config.updater?.timestamp)
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

/** 小标签：可见度 / 生命周期。配色对齐 web 的 nz-tag（开发中=蓝、预览=橙、已发布=绿） */
@Composable
internal fun InfoChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

internal fun visibilityColor(visibility: String?): Color = when (visibility) {
    "public" -> Green
    "private" -> Gray500
    else -> Gray500
}

internal fun lifecycleColor(lifecycle: String?): Color = when (lifecycle ?: "development") {
    "development" -> Blue500
    "preview" -> Orange
    "released" -> Green
    else -> Gray500
}
