package cc.openxiot.wematrix.remote

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.remote.player.compose.ExperimentalRemotePlayerApi
import androidx.compose.remote.player.compose.RemoteComposePlayerFlags
import androidx.compose.remote.player.compose.RemoteDocumentPlayer
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@SuppressLint("RestrictedApi")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalRemotePlayerApi::class)
@Composable
fun RemotePreviewScreen(
    onBack: () -> Unit
) {
    val serverBase = remember { "http://192.168.5.80:8080" }
    var endpoint by remember { mutableStateOf("/remote-compose/home-screen?userName=AndroidPreview") }
    var rcBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RemoteCompose 预览", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                error = null
                                try {
                                    rcBytes = fetchRemoteCompose(serverBase + endpoint)
                                } catch (e: Exception) {
                                    error = e.message ?: "未知错误"
                                }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text("服务端端点") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { endpoint = "/remote-compose/hello" },
                    label = { Text("Hello", style = MaterialTheme.typography.labelSmall) }
                )
                AssistChip(
                    onClick = { endpoint = "/remote-compose/home-screen?userName=Debug" },
                    label = { Text("HomeScreen", style = MaterialTheme.typography.labelSmall) }
                )
                AssistChip(
                    onClick = { endpoint = "/remote-compose/device-panel?deviceName=Dev-A&temperature=28.5&humidity=65&status=online" },
                    label = { Text("设备面板", style = MaterialTheme.typography.labelSmall) }
                )
            }

            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (rcBytes != null) {
                    RemoteComposePlayerFlags.shouldPlayerWrapContentSize = true
                    val remoteDoc = remember(rcBytes) { RemoteDocument(rcBytes!!) }
                    val doc = remoteDoc.document
                    RemoteDocumentPlayer(
                        doc,
                        doc.width.coerceAtLeast(1),
                        doc.height.coerceAtLeast(1),
                        Modifier.fillMaxSize(),
                        0,
                        { _ -> },
                        { _ -> },
                        { id, action -> println("[RemoteCompose] Action($id): $action") },
                        { _, _, _ -> },
                        null,
                        null
                    )
                } else {
                    Text(
                        "点击刷新按钮加载 RemoteCompose UI",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private suspend fun fetchRemoteCompose(url: String): ByteArray = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    val request = Request.Builder().url(url).build()
    val response = client.newCall(request).execute()
    if (!response.isSuccessful) {
        throw RuntimeException("HTTP ${response.code}: ${response.body?.string() ?: "no body"}")
    }
    response.body?.bytes() ?: throw RuntimeException("Empty response body")
}
