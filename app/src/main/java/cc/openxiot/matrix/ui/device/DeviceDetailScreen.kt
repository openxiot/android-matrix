package cc.openxiot.matrix.ui.device

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.matrix.MatrixApp
import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.api.RetrofitClient
import cc.openxiot.matrix.data.api.SpaceEntity
import cc.openxiot.matrix.ui.project.ProjectViewModel
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import cc.openxiot.matrix.ui.components.AppWebView
import cc.openxiot.matrix.ui.components.OnlineIndicator
import cc.openxiot.matrix.ui.core.asString
import cc.openxiot.matrix.ui.modbus.DeviceServicesCard
import cc.openxiot.matrix.ui.modbus.ModbusServiceViewModel
import cc.openxiot.matrix.util.Constants
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    did: String,
    onBack: () -> Unit,
    /** 点服务行：传当前根空间（服务接口的鉴权作用域）与服务 id */
    onServiceClick: ((spaceId: String, serviceId: String) -> Unit)? = null,
    viewModel: ProjectViewModel = viewModel(),
    serviceViewModel: ModbusServiceViewModel = viewModel()
) {
    val treeState by viewModel.treeState.collectAsState()
    val projectState by viewModel.projectState.collectAsState()
    val servicesState by serviceViewModel.deviceServices.collectAsState()
    // 服务接口路径里的 spaceId 是鉴权作用域，统一用当前项目根空间（口径同 web）
    val rootSpaceId = projectState.currentRootId
    val device = treeState.devices.find { it.did == did }
    val model = device?.type?.let { extractModelFromUrn(it) }
    val productName = model?.let { treeState.productNames[it] }
    val productIcon = model?.let { treeState.productIcons[it] }

    // 设备控制页 url：按 deviceType 取 category=mobile 的控制页，挑最新版本带 web.url 的，
    // 再追加宿主注入参数（server/spaceId/did/token，见 loadControlUrl）。空则详情页不显示控制区块。
    val controlUrl by produceState(initialValue = "", device?.type, rootSpaceId, did) {
        value = loadControlUrl(deviceType = device?.type, spaceId = rootSpaceId, did = did)
    }

    // 服务按 did 取（/parent 只按 did 过滤），根空间只作鉴权作用域
    LaunchedEffect(did, rootSpaceId) {
        if (rootSpaceId != null) serviceViewModel.loadByDevice(rootSpaceId, did)
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
                        text = productName ?: stringResource(R.string.device_detail_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (device == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.device_not_found), style = MaterialTheme.typography.bodyLarge)
                }
                return@Scaffold
            }

            // Status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            if (productIcon != null) {
                                AsyncImage(
                                    model = productIcon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.DevicesOther,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = productName ?: stringResource(R.string.common_unknown_product),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OnlineIndicator(isOnline = device.online == true)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(if (device.online == true) R.string.common_online else R.string.common_offline),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (device.online == true) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Device ID
            DetailRow(label = stringResource(R.string.device_id_label), value = device.did ?: "-")
            HorizontalDivider()

            // Device type
            val typeName = extractTypeName(device.type) ?: device.type ?: "-"
            DetailRow(label = stringResource(R.string.device_type_label), value = typeName)
            HorizontalDivider()

            // Protocol
            DetailRow(label = stringResource(R.string.common_protocol_label), value = device.protocol ?: "-")
            HorizontalDivider()

            // Online status
            DetailRow(label = stringResource(R.string.device_online_label), value = stringResource(if (device.online == true) R.string.common_online else R.string.common_offline))
            HorizontalDivider()

            // Last online
            if (device.lastOnline != null) {
                DetailRow(label = stringResource(R.string.device_last_online_label), value = formatUtcToLocal(device.lastOnline))
                HorizontalDivider()
            }

            // Last offline
            if (device.lastOffline != null) {
                DetailRow(label = stringResource(R.string.device_last_offline_label), value = formatUtcToLocal(device.lastOffline))
                HorizontalDivider()
            }

            // Space
            if (device.space?.spaceId != null) {
                val spaceName = findSpaceById(treeState.rootSpace, device.space.spaceId)?.name
                DetailRow(label = stringResource(R.string.device_space_label), value = spaceName ?: device.space.spaceId ?: "-")
            }

            // 设备控制页（category=mobile，服务端按 deviceType 下发的第三方页面，WebView 顶层加载）
            if (controlUrl.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.device_control_card_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp)
                        )
                        AppWebView(
                            url = controlUrl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(560.dp)
                        )
                    }
                }
            }

            // Services（只读入口：点进服务详情页调用方法；新建/编辑/删除在移动端不做）
            DeviceServicesCard(
                services = servicesState.services,
                isLoading = servicesState.isLoading && rootSpaceId != null,
                error = servicesState.error?.asString(),
                onRetry = { rootSpaceId?.let { serviceViewModel.loadByDevice(it, did) } },
                onServiceClick = { serviceId ->
                    rootSpaceId?.let { onServiceClick?.invoke(it, serviceId) }
                }
            )
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

/**
 * Extract the model field from a device type URN.
 * Format: urn:<ns>:device:<name>:<value>:<organization>:<model>:<version>
 */
private fun extractModelFromUrn(urn: String?): String? {
    if (urn == null) return null
    val parts = urn.split(":")
    return if (parts.size >= 7) parts[6] else null
}

/**
 * Extract the human-readable type name (4th field) from a device type URN.
 */
private fun extractTypeName(urn: String?): String? {
    if (urn == null) return null
    val parts = urn.split(":")
    return if (parts.size >= 4) parts[3] else null
}

private fun formatUtcToLocal(utcTime: String?): String {
    if (utcTime == null) return "-"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(utcTime)
        if (date != null) {
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            outputFormat.timeZone = TimeZone.getDefault()
            outputFormat.format(date)
        } else utcTime
    } catch (_: Exception) { utcTime }
}

/** 设备控制页 category：移动端详情页内嵌的是 mobile 版控制页（web 详情页内嵌的是 tablet 版）。 */
private const val CONTROL_CATEGORY_MOBILE = "mobile"

/**
 * 取设备控制页 url：按 deviceType 拉 category=mobile 的控制器列表，挑 version.code 最大且带 web.url 的，
 * 再追加宿主注入参数 —— 与 web 端详情页的跨域 iframe 参数一致（见 webapp-matrix 的 decorateFrameUrl）：
 * - `server`：矩阵后端地址（Constants.SITE_BASE_URL），控制页据此拼 API base；
 * - `spaceId`：当前项目根空间（鉴权作用域，同本页 getDevice/service 的 spaceId）；
 * - `did`：设备 did；
 * - `token`：当前登录用户 token。
 * 查不到（设备无 type / 无控制页 / 接口失败）返回空串，详情页不显示控制区块。失败静默是诚实降级。
 */
private suspend fun loadControlUrl(deviceType: String?, spaceId: String?, did: String): String {
    if (deviceType.isNullOrBlank() || spaceId == null) return ""
    val controllers = runCatching {
        val r = RetrofitClient.productService.getControllersByDeviceType(deviceType, CONTROL_CATEGORY_MOBILE)
        if (r.isSuccessful && r.body()?.success == true) r.body()?.data.orEmpty() else emptyList()
    }.getOrDefault(emptyList())

    val best = controllers.asSequence()
        .filter { !it.web?.url.isNullOrBlank() }
        .maxByOrNull { it.version?.code ?: 0 }
        ?: return ""

    return Uri.parse(best.web!!.url).buildUpon()
        .appendQueryParameter("server", Constants.SITE_BASE_URL)
        .appendQueryParameter("spaceId", spaceId)
        .appendQueryParameter("did", did)
        .appendQueryParameter("token", MatrixApp.instance.tokenManager.token.orEmpty())
        .build()
        .toString()
}

private fun findSpaceById(root: SpaceEntity?, id: String?): SpaceEntity? {
    if (root == null || id == null) return null
    if (root.id == id) return root
    return root.children?.firstNotNullOfOrNull { findSpaceById(it, id) }
}
