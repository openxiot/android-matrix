package cc.openxiot.wematrix.ui.device

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.openxiot.wematrix.data.api.RetrofitClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceOperationScreen(
    deviceType: String,
    deviceDid: String,
    spaceId: String,
    onBack: () -> Unit
) {
    var instanceJson by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(deviceType) {
        try {
            val response = RetrofitClient.productService.getProductInstance(deviceType)
            if (response.isSuccessful && response.body()?.success == true) {
                instanceJson = response.body()!!.data
            } else {
                errorMsg = response.body()?.message ?: "获取设备定义失败"
            }
        } catch (e: Exception) {
            errorMsg = e.message
        }
        isLoading = false
    }

    val switches = remember(instanceJson) {
        parseSwitches(instanceJson)
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
                        text = "设备操作",
                        style = MaterialTheme.typography.titleLarge,
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                errorMsg != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                }
                switches.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("未找到可操作的开关")
                }
                else -> {
                    switches.forEach { switch ->
                        SwitchCard(
                            label = switch.label,
                            isOn = switch.isOn ?: false,
                            onToggle = { /* TODO: write property */ }
                        )
                    }
                }
            }
        }
    }
}

private data class SwitchInfo(
    val label: String,
    val isOn: Boolean?,
    val siid: Int,
    val piid: Int
)

private fun parseSwitches(instance: Map<String, Any?>?): List<SwitchInfo> {
    if (instance == null) return emptyList()
    val services = instance["services"] as? List<Map<String, Any?>> ?: return emptyList()
    val result = mutableListOf<SwitchInfo>()
    for (service in services) {
        val serviceIid = service["iid"] as? Int ?: continue
        val properties = service["properties"] as? List<Map<String, Any?>> ?: continue
        for (prop in properties) {
            val access = prop["access"] as? List<String> ?: continue
            val format = prop["format"] as? String ?: continue
            if ("write" in access && format == "bool") {
                val propIid = prop["iid"] as? Int ?: continue
                val desc = (prop["description"] as? Map<String, Any?>)?.let {
                    (it["zh-CN"] as? String) ?: (it["en"] as? String)
                } ?: (prop["description"] as? String)
                result.add(SwitchInfo(
                    label = desc ?: "开关 ${serviceIid}.${propIid}",
                    isOn = null,
                    siid = serviceIid,
                    piid = propIid
                ))
            }
        }
    }
    return result
}

@Composable
private fun SwitchCard(
    label: String,
    isOn: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOn) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isOn) Icons.Default.PowerSettingsNew else Icons.Default.PowerOff,
                contentDescription = null,
                tint = if (isOn) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = isOn, onCheckedChange = { onToggle() })
        }
    }
}
