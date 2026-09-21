package cc.openxiot.wematrix.ui.modbus

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cc.openxiot.wematrix.R
import cc.openxiot.wematrix.data.api.ModbusCommand
import cc.openxiot.wematrix.ui.core.asString
import cc.openxiot.wematrix.ui.theme.Blue500
import cc.openxiot.wematrix.ui.theme.Red

/**
 * 「命令」预览对话框：一条功能码动作对应的**请求帧 + 应答帧 + 异常应答帧**，
 * 三者都带字段级解析。对齐 webapp-matrix 的
 * `pages/main/modbus/editor/request/request.frame.dialog.component.*`。
 *
 * 三张帧都是本端算出来的（见 [RequestFrame]），**不发送任何东西**：读应答的数据区按字节数补零
 * （真实数据由设备返回），异常应答的异常码与 CRC 待设备返回、以 `??` 占位。
 * 现场排障要的就是这串十六进制 —— 故每一段都能一键复制，粘进串口工具即可。
 */
@Composable
fun CommandFrameDialog(
    command: ModbusCommand,
    slaveId: Int?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val request = buildRequestFrame(command, slaveId)
    val preview = buildResponseFrame(command, slaveId)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    // 高上限而不是铺满：帧解析行数不多时对话框跟着内容走，不空一大片
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 抬头：名称 + 功能码（写功能码标红，与点表列表一致）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = command.name?.takeIf { it.isNotBlank() } ?: "-",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = command.fc ?: "-",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isWriteFc(command.fc)) Red else Blue500
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = fcLabel(command.fc).asString(),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (request == null || preview == null) {
                    // 数据不完整（缺从站地址、必要字段没填）时不给半张帧 —— 半张帧比没有更容易被抄错
                    Text(
                        text = stringResource(R.string.modbus_frame_incomplete),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    FrameSection(
                        // 与「服务详情」里那处小节标题同一句，共用一个 key
                        title = stringResource(R.string.modbus_frame_request),
                        hex = request.hex,
                        count = request.count,
                        notes = listOf(stringResource(R.string.modbus_frame_notes_request)),
                        parts = describeRequestFrame(command, request),
                        context = context
                    )
                    FrameSection(
                        title = stringResource(R.string.modbus_frame_response),
                        hex = preview.frame.hex,
                        count = preview.frame.count,
                        notes = buildList {
                            add(stringResource(R.string.modbus_frame_notes_response))
                            // 读应答的数据区由设备返回，帧里按字节数补的是示例值
                            if (preview.frame.sample) {
                                add(stringResource(R.string.modbus_frame_notes_sample))
                            }
                        },
                        parts = describeResponseFrame(command, preview.frame),
                        context = context
                    )
                    FrameSection(
                        title = stringResource(R.string.modbus_frame_exception),
                        hex = preview.exception.hex,
                        count = preview.exception.count,
                        notes = listOf(stringResource(R.string.modbus_frame_notes_exception)),
                        parts = describeResponseFrame(command, preview.exception),
                        context = context,
                        danger = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
                }
            }
        }
    }
}

/** 一段帧：标题 + 十六进制 + 元信息 + 字段级解析 */
@Composable
private fun FrameSection(
    title: String,
    hex: String,
    count: Int,
    notes: List<String>,
    parts: List<FramePart>,
    context: Context,
    danger: Boolean = false
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = if (danger) Red else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp)
    )

    // 帧可以很长（10 多寄存器能到几十字节），横滑而不换行 —— 换行后就没法照着抄了
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = hex,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            notes.forEach { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.modbus_frame_byte_total, count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 剪贴板条目的名字也得跟着语言走，故在组合里取好再传进去
        val clipboardLabel = stringResource(R.string.modbus_frame_clipboard_label)
        TextButton(onClick = { copyToClipboard(context, hex, clipboardLabel) }) {
            Text(stringResource(R.string.modbus_frame_copy))
        }
    }

    Text(
        text = stringResource(R.string.modbus_frame_structure),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    parts.forEach { part -> PartRow(part) }
}

/** 解析出来的一行：字段名 / 该字段的字节 / 解读（一行文本或数据区的多行） */
@Composable
private fun PartRow(part: FramePart) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = part.label.asString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = part.hex,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(72.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            when {
                part.text != null -> Text(
                    text = part.text.asString(),
                    style = MaterialTheme.typography.labelSmall
                )
                // 数据区逐位 / 逐寄存器：一行一项
                part.lines.isNotEmpty() -> part.lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
                else -> Text(text = "-", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * 复制到系统剪贴板。
 *
 * 走平台的 `ClipboardManager` 而不是 Compose 的剪贴板 API：后者在近几个版本里换过一次
 * （`LocalClipboardManager` → `LocalClipboard`），而这一段只是想往剪贴板里放一串文本，
 * 用平台服务不必跟着 Compose 的 API 迁移走。
 */
private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
