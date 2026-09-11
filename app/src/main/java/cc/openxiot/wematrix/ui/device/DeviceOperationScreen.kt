package cc.openxiot.wematrix.ui.device

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.openxiot.wematrix.ui.components.AppWebView
import cc.openxiot.wematrix.util.Constants

/**
 * 设备操作页：加载第三方设备页面的 WebView，与 webapp-matrix 的设备详情页口径一致
 * （那边是跨域 iframe，这里是顶层 WebView，差异见 AppWebView 的注释）。
 *
 * deviceType / deviceDid / spaceId 目前**没有用到**：地址还是一张「全设备同一个页面」的写死常量。
 * 保留这三个参数是因为路由串 `device_operation/{did}?type={type}&spaceId={spaceId}` 与三处调用点
 * 都按它传参，且将来做「型号 → 地址」映射表时正好要用。
 *
 * TODO: 按 deviceType（型号）映射到不同的第三方页面地址
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceOperationScreen(
    deviceType: String,
    deviceDid: String,
    spaceId: String,
    onBack: () -> Unit
) {
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        AppWebView(
            url = Constants.DEVICE_OPERATION_URL,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
