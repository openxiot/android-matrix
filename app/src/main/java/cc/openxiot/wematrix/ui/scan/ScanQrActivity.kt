package cc.openxiot.wematrix.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import cc.openxiot.wematrix.AppLocale
import cc.openxiot.wematrix.R
/**
 * 添加设备的扫码页（竖屏）。支持扫码二维码，也支持扫码设备标签上的 IMEI（一维/二维码）。
 * 页面为纯 Compose 自绘，提供：左上角关闭、中间取景框、底部手电筒开关。
 */
class ScanQrActivity : ComponentActivity() {

    /** 应用语言。⚠️ 每个带界面的 Activity 都要有这一句，漏了就永远跟系统语言走。 */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanQrScreen(
                onClose = { finish() },
                onScanned = { text ->
                    if (text.isNotBlank()) {
                        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT, text))
                        finish()
                    }
                }
            )
        }
    }

    companion object {
        const val EXTRA_RESULT = "scan_result_text"
    }
}

/** 允许扫描的条码格式：二维码 + 设备标签常用的一维/DataMatrix */
private val SCAN_FORMATS: List<BarcodeFormat> = listOf(
    BarcodeFormat.QR_CODE,
    BarcodeFormat.CODE_128,
    BarcodeFormat.CODE_39,
    BarcodeFormat.DATA_MATRIX,
    BarcodeFormat.ITF
)

private val ScanBlue = Color(0xFF0D84FF)

@Composable
private fun ScanQrScreen(
    onClose: () -> Unit,
    onScanned: (String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            ScannerCameraContent(
                onClose = onClose,
                onScanned = onScanned
            )
        } else {
            CameraPermissionPlaceholder(
                onClose = onClose,
                onRetry = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )
        }
    }
}

/** 相机预览 + 扫码 UI 覆盖层 */
@Composable
private fun ScannerCameraContent(
    onClose: () -> Unit,
    onScanned: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val barcodeRef = remember { mutableStateOf<BarcodeView?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    // 跟随 Activity 生命周期开关相机
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> barcodeRef.value?.resume()
                Lifecycle.Event.ON_PAUSE -> barcodeRef.value?.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            barcodeRef.value?.pause()
        }
    }

    fun toggleTorch() {
        val view = barcodeRef.value ?: return
        view.setTorch(!torchOn)
        torchOn = !torchOn
    }

    Box(Modifier.fillMaxSize()) {
        // 相机预览（放最底层）
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                BarcodeView(ctx).apply {
                    decoderFactory = DefaultDecoderFactory(SCAN_FORMATS)
                    keepScreenOn = true
                    val self = this
                    var delivered = false
                    decodeContinuous(object : BarcodeCallback {
                        override fun barcodeResult(result: BarcodeResult) {
                            if (delivered) return
                            val text = result.text
                            if (text.isNullOrBlank()) return
                            delivered = true
                            self.pause()
                            onScanned(text)
                        }
                    })
                }
            },
            update = { view ->
                barcodeRef.value = view
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    view.resume()
                }
            }
        )

        // UI 覆盖层：关闭按钮 / 取景框 / 提示 / 手电筒
        ScanOverlay(
            torchOn = torchOn,
            onToggleTorch = ::toggleTorch,
            onClose = onClose
        )
    }
}

/** 扫码界面 UI 覆盖层（关闭 / 取景框 / 提示 / 手电筒） */
@Composable
private fun ScanOverlay(
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    onClose: () -> Unit
) {
    val scanLine by rememberInfiniteTransition(label = "scanLine").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLineValue"
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frameSize = minOf(maxWidth * 0.72f, 320.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部：关闭按钮（左上角）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = Color.White
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // 中间取景框
            ScanViewfinder(frameSize = frameSize, scanFraction = scanLine)

            Spacer(Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.scan_hint),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            // 底部：手电筒开关
            TorchButton(
                torchOn = torchOn,
                onToggle = onToggleTorch,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 居中的取景框：描边 + 四角高亮 + 上下移动的扫描线 */
@Composable
private fun ScanViewfinder(frameSize: Dp, scanFraction: Float) {
    Canvas(
        modifier = Modifier.size(frameSize)
    ) {
        val border = 1.dp.toPx()
        val radius = 18.dp.toPx()
        val colorWhite = Color.White.copy(alpha = 0.22f)

        // 边框
        drawRoundRect(
            color = colorWhite,
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = border)
        )

        // 四角高亮
        drawCorners(
            color = ScanBlue,
            cornerRadius = radius,
            thickness = 3.dp.toPx(),
            arm = 22.dp.toPx()
        )

        // 扫描线
        val lineHeight = 2.5.dp.toPx()
        val inset = 20.dp.toPx()
        val y = inset + scanFraction * (size.height - 2 * inset - lineHeight)
        drawLine(
            color = ScanBlue.copy(alpha = 0.9f),
            start = Offset(inset, y),
            end = Offset(size.width - inset, y),
            strokeWidth = lineHeight,
            cap = StrokeCap.Round
        )
    }
}

/** 在圆角描边外的直线段上绘制四角高亮 */
private fun DrawScope.drawCorners(
    color: Color,
    cornerRadius: Float,
    thickness: Float,
    arm: Float
) {
    val ins = thickness / 2f
    val w = size.width
    val h = size.height

    // 顶/底/左/右 直线段（圆角之外）各画一小段高亮
    drawLine(color, Offset(cornerRadius, ins), Offset(cornerRadius + arm, ins), thickness, StrokeCap.Round)           // 上左
    drawLine(color, Offset(w - cornerRadius - arm, ins), Offset(w - cornerRadius, ins), thickness, StrokeCap.Round) // 上右
    drawLine(color, Offset(ins, cornerRadius), Offset(ins, cornerRadius + arm), thickness, StrokeCap.Round)           // 左
    drawLine(color, Offset(w - ins, cornerRadius), Offset(w - ins, cornerRadius + arm), thickness, StrokeCap.Round)  // 右
    drawLine(color, Offset(cornerRadius, h - ins), Offset(cornerRadius + arm, h - ins), thickness, StrokeCap.Round)           // 下左
    drawLine(color, Offset(w - cornerRadius - arm, h - ins), Offset(w - cornerRadius, h - ins), thickness, StrokeCap.Round) // 下右
    drawLine(color, Offset(ins, h - cornerRadius - arm), Offset(ins, h - cornerRadius), thickness, StrokeCap.Round)          // 左下
    drawLine(color, Offset(w - ins, h - cornerRadius - arm), Offset(w - ins, h - cornerRadius), thickness, StrokeCap.Round) // 右下
}

/** 底部手电筒开关 */
@Composable
private fun TorchButton(
    torchOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0x66000000))
        ) {
            Icon(
                if (torchOn) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
                contentDescription = stringResource(
                    if (torchOn) R.string.scan_torch_off else R.string.scan_torch_on
                ),
                tint = Color.White
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.scan_torch),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp
        )
    }
}

/** 无相机权限时的占位页 */
@Composable
private fun CameraPermissionPlaceholder(
    onClose: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = Color.White
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.scan_permission_required),
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        ScanTextButton(text = stringResource(R.string.scan_grant), onClick = onRetry)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ScanTextButton(text: String, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = ScanBlue
        )
    ) {
        Text(text, color = Color.White)
    }
}
