package cc.openxiot.wematrix.remote

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.remote.creation.compose.action.hostAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnitType.Companion.Sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun homeScreenContent(userName: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(
            text = "Hello, $userName!",
            color = Color.White
        )
        Box(Modifier.height(16.dp)) {}
        Text(
            text = "Welcome to the RemoteCompose IoT Dashboard",
        )
        Box(Modifier.height(16.dp)) {}
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            Text(
                text = "Go to Profile",
            )
        }
    }
}

/**
 * RemoteCompose UI 函数（服务于可序列化场景）。
 * 预览时通过 RemoteContentPreview 录制后回放，无需兼容本地预览。
 */

/**
 * 本地预览用的函数（与 RemoteCompose UI 视觉一致，使用真实 Compose 组件）。
 * 在 IDE 中可直接预览。
 */
@Preview(
    name = "homescreen",
    group = "home",
    showBackground = true,
    backgroundColor = 0xFF1C1C1E,
    uiMode = Configuration.UI_MODE_TYPE_NORMAL,
    device = "id:Nexus S"
)
@Composable
fun PreviewRemoteHomeScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Hello, 张三!",
            fontSize = 24.sp
        )
        Button(onClick = { /* 预览时不可点击 */ }) {
            Text("Go Profile")
        }

        homeScreenContent("xxx");
    }
}
