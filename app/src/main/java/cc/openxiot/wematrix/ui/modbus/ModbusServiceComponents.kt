package cc.openxiot.wematrix.ui.modbus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.openxiot.wematrix.data.api.ModbusService
import cc.openxiot.wematrix.data.api.ModbusServiceBrief
import cc.openxiot.wematrix.ui.theme.Blue500

/**
 * 服务在两处的展示件：
 * - [ModbusServiceRow]：项目页空间树里，挂在设备节点下的服务行；
 * - [DeviceServicesCard]：设备详情页内容区底部那张「服务」卡片。
 *
 * 两处都是**只读入口**：点进去看详情、在详情页调用方法；没有新建/编辑/删除按钮
 * （那些需要空间管理员，移动端不做）。
 */

/**
 * 空间树 / 设备列表里的服务行：缩进与形状对齐同页的设备卡片，只是整体小一号。
 *
 * [baseIndent] 是同级设备卡片的起始缩进 —— 项目页的设备是 12dp、设备页是 16dp，
 * 两边都按自己的基准传，服务行才能和设备名对齐。（排在 `onClick` 前面，好让尾随 lambda 还是 `onClick`。）
 */
@Composable
fun ModbusServiceRow(
    service: ModbusServiceBrief,
    depth: Int,
    /** 同级设备卡片的起始缩进：项目页 12dp、设备页 16dp */
    baseIndent: Dp = 12.dp,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = baseIndent + (depth * 20).dp,
                end = 16.dp,
                top = 2.dp,
                bottom = 2.dp
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // web 的服务行用 nz-icon nzType="api"，这里取同一个意象
            Icon(
                Icons.Default.Api,
                contentDescription = null,
                tint = Blue500,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            // 名字占满剩余空间（过长的省略），把「服务」标签顶到 chevron 前面
            Text(
                text = service.name?.takeIf { it.isNotBlank() } ?: "未命名服务",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            // 蓝底「服务」标签，配色对齐 web 的 nz-tag nzColor="blue"
            InfoChip("服务", Blue500)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 设备详情页内容区底部的「服务」卡片。
 *
 * 数据源是 `/modbus/service/parent/{spaceId}/{did}`（只按 did 过滤），
 * 与空间图那份精简视图不同：设备搬过家、服务里记的落点空间陈旧时，这里仍然不漏。
 */
@Composable
fun DeviceServicesCard(
    services: List<ModbusService>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onServiceClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "服务",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                if (!isLoading && error == null && services.isNotEmpty()) {
                    Text(
                        text = "（${services.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }

                error != null -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onRetry) { Text("重试") }
                }

                services.isEmpty() -> Text(
                    text = "该设备下还没有服务",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                else -> services.forEach { service ->
                    ServiceCardRow(service) { service.id?.let(onServiceClick) }
                }
            }
        }
    }
}

/** 卡片里的一行服务：名称 + 方法数 + chevron */
@Composable
private fun ServiceCardRow(service: ModbusService, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Api,
            contentDescription = null,
            tint = Blue500,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = service.name?.takeIf { it.isNotBlank() } ?: "未命名服务",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${service.functions.size} 个方法",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
