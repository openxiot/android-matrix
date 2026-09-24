package cc.openxiot.matrix.ui.device

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.openxiot.matrix.MatrixApp
import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.api.RetrofitClient
import cc.openxiot.matrix.ui.components.AppWebView
import cc.openxiot.matrix.ui.device.controller.DeviceControllerScreen
import cc.openxiot.matrix.util.Constants

/**
 * 设备操作页：加载第三方设备**控制页**（category=mobile）的 WebView，与 webapp-matrix
 * 设备详情页的内嵌 iframe 口径一致（那边是跨域 iframe，这里是顶层 WebView，差异见 AppWebView 的注释）。
 *
 * 控制页地址不写死：按 `deviceType`（型号）从服务端拉 category=mobile 的最新版本控制页，再追加
 * 宿主注入参数 `server` / `spaceId` / `did` / `token` —— 参数的取法与 web 端 decorateFrameUrl 一致
 * （见 [loadControlUrl]）。取不到（设备无 type / 无该型号控制页 / 接口失败）则显示占位提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceOperationScreen(
    deviceType: String,
    deviceDid: String,
    spaceId: String,
    onBack: () -> Unit
) {
    // null = 解析中；非空 = 控制页 url；空串 = 解析完成但没有可用控制页（无 type / 无该型号页面 / 接口失败）
    val controlUrl by produceState<String?>(initialValue = null, deviceType, deviceDid, spaceId) {
        value = loadControlUrl(deviceType = deviceType, spaceId = spaceId, did = deviceDid)
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
                        text = stringResource(R.string.device_operation_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        when {
            controlUrl == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            controlUrl!!.isNotEmpty() -> {
                // 设备控制页是固定看板：锁死竖向拖动，仅保留页面自带的顶部下拉刷新
                AppWebView(
                    url = controlUrl!!,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    lockScroll = true
                )
            }
            else -> {
                // 双模式兜底：没有配置控制页的产品，用原生动态渲染的通用控制界面
                //（一个服务一张卡片，读/写/执行），对齐 webapp DeviceControllerComponent。
                DeviceControllerScreen(
                    spaceId = spaceId,
                    did = deviceDid,
                    type = deviceType,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
        }
    }
}

/** 设备控制页 category：移动端设备操作页加载的是 mobile 版控制页（web 详情页内嵌的是 tablet 版）。 */
private const val CONTROL_CATEGORY_MOBILE = "mobile"

/**
 * 取设备控制页 url：按 deviceType 拉 category=mobile 的控制器列表，挑 version.code 最大且带 web.url 的，
 * 再追加宿主注入参数 —— 与 web 端详情页的跨域 iframe 参数一致（见 webapp-matrix 的 decorateFrameUrl）：
 * - `server`：矩阵后端地址（Constants.SITE_BASE_URL），控制页据此拼 API base；
 * - `spaceId`：设备所在空间 ID（本页收到的 spaceId）；
 * - `did`：设备 did；
 * - `token`：当前登录用户 token。
 * 查不到（设备无 type / 无控制页 / 接口失败）返回空串。失败静默是诚实降级。
 */
private suspend fun loadControlUrl(deviceType: String?, spaceId: String, did: String): String {
    if (deviceType.isNullOrBlank() || spaceId.isBlank()) return ""
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