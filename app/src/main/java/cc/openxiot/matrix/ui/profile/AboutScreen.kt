package cc.openxiot.matrix.ui.profile

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.openxiot.matrix.AppUpdate
import cc.openxiot.matrix.BuildConfig
import cc.openxiot.matrix.R
import cc.openxiot.matrix.UpdateState
import cc.openxiot.matrix.data.repository.UpdateInfo
import cc.openxiot.matrix.ui.core.asString
import cc.openxiot.matrix.ui.theme.Gray500
import cc.openxiot.matrix.ui.theme.Orange
import cc.openxiot.matrix.util.ApkInstaller

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by AppUpdate.state.collectAsStateWithLifecycle()

    // 进页面静默查一次。AppUpdate.autoCheck 自带节流与「只在 Idle 时动手」，
    // 所以旋转、返回重跑它都不会多打一次网络。
    LaunchedEffect(Unit) { AppUpdate.autoCheck() }

    // 授权页回来之后必须**重新问一次系统**。不能拿那次跳转的 resultCode 判断用户开没开
    // 开关 —— 那个回调永远是 RESULT_CANCELED，拿它判断必然得出「用户拒绝了」。
    var canInstall by remember { mutableStateOf(ApkInstaller.canInstall(context)) }
    var metered by remember { mutableStateOf(isMeteredNetwork(context)) }
    LifecycleResumeEffect(Unit) {
        canInstall = ApkInstaller.canInstall(context)
        metered = isMeteredNetwork(context)
        onPauseOrDispose { }
    }

    var dialogVisible by rememberSaveable { mutableStateOf(false) }

    // 发现新版本 → 弹出说明。用户进这一页就是为了知道有没有新版，查到就直说，
    // 别让他再去点一下按钮才看得到。key 取布尔量：只在「有 / 没有」翻转时才重启，
    // 下载进度每 64KB 变一次状态也不会把协程重启几百遍。
    LaunchedEffect(state is UpdateState.Available) {
        if (state is UpdateState.Available) dialogVisible = true
    }
    // 下载完成 → 自动换成「安装」形态。用户刚盯着进度条等完 54MB，人就在这块屏幕上。
    LaunchedEffect(state is UpdateState.Ready) {
        if (state is UpdateState.Ready) dialogVisible = true
    }

    // 取 `LocalContext`（= 套过 locale 的 Activity）而不是 `applicationContext`：
    // 后者是 Application 那份，要它跟 UI 一致得指望 MatrixApp 也包了 baseContext
    val toast: (Int) -> Unit = { resId ->
        Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
    }

    val install: () -> Unit = {
        val ready = state as? UpdateState.Ready
        when {
            ready == null -> Unit
            // 没授权就先去开开关。注意返回之后 canInstall 由上面的 ON_RESUME 刷新，
            // 用户不必退出页面重进。
            !canInstall -> {
                if (!ApkInstaller.openInstallPermissionSettings(context)) {
                    toast(R.string.about_install_settings_failed)
                }
            }
            !ApkInstaller.install(context, ready.file) ->
                toast(R.string.about_no_installer)
            else -> dialogVisible = false
        }
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                    Text(
                        text = stringResource(R.string.about_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        // 必须能滚动：加了一整块更新区之后，横屏或最大字号下内容会超出一屏，
        // 而居中的 Column 是把溢出往**上下两头**推的 —— 不滚的话上下一起被裁掉。
        //
        // 这里也不能省掉 heightIn(min = maxHeight)：verticalScroll 是用「无限高」去量
        // 子项的，不给一个「至少一屏」的下限，Arrangement.Center 会**静默退化成顶部对齐**
        // （不报错、不警告，只是看起来不再居中）。
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.openxiot_blue),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 走 BuildConfig 而不是 getPackageInfo：少一次跨进程查询与两个 try/catch，
                // 值与已安装的包是同一个（都在构建期定下来，-P 覆盖也走同一条路）。
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Gray500
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Build ${BuildConfig.VERSION_CODE}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray500
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                UpdateSection(
                    state = state,
                    canInstall = canInstall,
                    metered = metered,
                    onCheck = { AppUpdate.check() },
                    onDownload = { AppUpdate.download() },
                    onInstall = install
                )
            }
        }
    }

    val dialogInfo = when (val current = state) {
        is UpdateState.Available -> current.info
        is UpdateState.Ready -> current.info
        else -> null
    }
    if (dialogVisible && dialogInfo != null) {
        val ready = state is UpdateState.Ready
        UpdateDialog(
            info = dialogInfo,
            ready = ready,
            onDismiss = { dialogVisible = false },
            onConfirm = {
                if (ready) {
                    install()
                } else {
                    // 开始下载就关掉对话框：这样「关掉对话框」与「离开页面」行为一致，
                    // 都不影响下载（下载挂在 AppUpdate 上，不挂在这块界面）。
                    dialogVisible = false
                    AppUpdate.download()
                }
            }
        )
    }
}

/**
 * 「版本更新」那一段：一个按钮 + 进度条 + 一行提示。整页只有这一个动作，所以按钮
 * 始终占同一个位置，靠文案与禁用态表达当前在做什么。
 */
@Composable
private fun UpdateSection(
    state: UpdateState,
    canInstall: Boolean,
    metered: Boolean,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    // 下载中不要禁掉按钮却又不给出口 —— 那两个态是「忙」，其余一律可点。
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading
    val label = when (state) {
        is UpdateState.Idle -> stringResource(R.string.about_check_update)
        is UpdateState.Checking -> stringResource(R.string.about_checking)
        is UpdateState.UpToDate -> stringResource(R.string.about_up_to_date)
        is UpdateState.Available -> stringResource(R.string.about_update_now)
        is UpdateState.CheckFailed -> stringResource(R.string.common_retry)
        is UpdateState.Downloading -> state.progress
            ?.let { stringResource(R.string.about_downloading_percent, (it * 100).toInt()) }
            ?: stringResource(R.string.about_downloading)
        is UpdateState.Ready -> stringResource(R.string.about_install_now)
        is UpdateState.DownloadFailed -> stringResource(R.string.about_retry_download)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                when (state) {
                    // 「已是最新」也保持可点：禁掉会把用户困住 —— 想再查一次只能退出页面重进
                    is UpdateState.UpToDate, is UpdateState.CheckFailed, is UpdateState.Idle ->
                        onCheck()
                    is UpdateState.Available, is UpdateState.DownloadFailed -> onDownload()
                    is UpdateState.Ready -> onInstall()
                    is UpdateState.Checking, is UpdateState.Downloading -> Unit
                }
            },
            enabled = !busy,
            modifier = Modifier
                .height(48.dp)
                .widthIn(min = 176.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
        }

        if (state is UpdateState.Downloading) {
            Spacer(modifier = Modifier.height(12.dp))
            val progress = state.progress
            if (progress == null) {
                // 响应没带 Content-Length：走不确定态，不要编一个百分比出来
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            }
        }

        val hint = when {
            // 两个失败态都带原因，直接显示在按钮下面：比 Toast 强，不会被错过，
            // 而且重试之后能自己消失
            state is UpdateState.CheckFailed -> state.message.asString()
            state is UpdateState.DownloadFailed -> state.message.asString()
            state is UpdateState.Ready && !canInstall ->
                stringResource(R.string.about_need_install_permission)
            state is UpdateState.Downloading -> stringResource(R.string.about_keep_app_alive)
            metered && (state is UpdateState.Available || state is UpdateState.Downloading) -> {
                val size = (state as? UpdateState.Available)?.info?.size
                    ?: (state as? UpdateState.Downloading)?.info?.size
                // 拆成**两条**资源而不是「前半句 + 后续片段」拼接：英文里「约 54 MB」
                // 与「费流量」两块的位置跟中文不一样，按中文语序拼出来是残句。
                size?.let { stringResource(R.string.about_metered_size, it) }
                    ?: stringResource(R.string.about_metered)
            }
            else -> null
        }
        hint?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = when {
                    state is UpdateState.CheckFailed || state is UpdateState.DownloadFailed ->
                        MaterialTheme.colorScheme.error
                    metered && state !is UpdateState.Downloading -> Orange
                    else -> Gray500
                }
            )
        }
    }
}

/**
 * 按**当前应用语言**挑一份更新说明。
 *
 * 挑在这里而不是 `UpdateRepository`：`AppUpdate` 是 object、跨 `recreate()` 存活，
 * 在那边挑就等于把语言钉在「查清单那一刻」—— 用户切完语言回到关于页，说明还是旧语言，
 * 而屏幕上别的字都变了。两份说明都留着，挑是界面层的事。
 *
 * 判据取 `LocalContext` 的 configuration，也就是 `attachBaseContext` 塞进去的那份；
 * **不是 `Locale.getDefault()`** —— 后者刻意保持跟随系统（改它会波及小数点分隔符等
 * 格式化行为，是另一件事）。
 */
@Composable
private fun notesFor(info: UpdateInfo): List<String> {
    val language = LocalContext.current.resources.configuration.locales.get(0)?.language
    return if (language == "zh") info.notesZh else info.notesEn
}

/**
 * 新版本说明 / 安装确认。没有复用 [cc.openxiot.matrix.ui.components.ConfirmDialog]：
 * 那个把「确认/取消」写死，且确认键染成 error 红 —— 那是给删除类操作用的，
 * 「更新」用红色按钮会把人吓退。
 */
@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    ready: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (ready) stringResource(R.string.about_install_dialog_title)
                else stringResource(R.string.about_new_version_found, info.version)
            )
        },
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(
                // AlertDialog 的 text 槽**自己不滚动**：更新说明列满十几条就会把按钮顶出
                // 屏幕，而对话框是点不动的。给个上限再套 verticalScroll。
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (ready) {
                    Text(
                        text = stringResource(R.string.about_ready_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    // releasedAt / size 原样显示，不做日期解析：项目里没有日期库，
                    // 对手写的字段做 LocalDate.parse 只会换来一个崩溃，收益是零
                    val meta = listOfNotNull(info.releasedAt, info.size)
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray500
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    val notes = notesFor(info)
                    if (notes.isEmpty()) {
                        Text(
                            text = stringResource(R.string.about_update_notes_empty),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        notes.forEach { note ->
                            // 「· 」是排版用的项目符号，不是待翻译文案，故不进资源
                            Text(
                                text = "· $note",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    if (ready) stringResource(R.string.about_install)
                    else stringResource(R.string.about_update_now)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    if (ready) stringResource(R.string.about_later)
                    else stringResource(R.string.common_cancel)
                )
            }
        }
    )
}

/**
 * 当前网络是不是计费网络（移动数据等）。只用来提示，**不阻断**：
 * 判断错了就不让用户下载，比多花点流量糟糕得多。所以拿不准（比如没有活动网络、
 * 拿不到 capabilities）一律返回 false，宁可不提示。
 */
private fun isMeteredNetwork(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}
