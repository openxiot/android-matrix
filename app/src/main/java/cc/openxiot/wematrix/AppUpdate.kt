package cc.openxiot.wematrix

import cc.openxiot.wematrix.R
import cc.openxiot.wematrix.data.repository.CheckOutcome
import cc.openxiot.wematrix.data.repository.UpdateInfo
import cc.openxiot.wematrix.data.repository.UpdateRepository
import cc.openxiot.wematrix.ui.core.UiText
import cc.openxiot.wematrix.ui.core.toUiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 版本更新的状态。**两个失败态是分开的**（[CheckFailed] / [DownloadFailed]），不是
 * 一个带可空 info 的 error —— 合成一个，UI 就得从「info 是不是空」去猜按钮该写
 * 「重试检查」还是「重试下载」。
 */
sealed interface UpdateState {
    /** 还没查过，或自动检查失败后静默退回这里。 */
    data object Idle : UpdateState
    data object Checking : UpdateState
    /** 本机已是最新。 */
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class CheckFailed(val message: UiText) : UpdateState
    /** [progress] 为 null 表示响应没带 Content-Length，进度不可知（UI 该用不确定进度条）。 */
    data class Downloading(val info: UpdateInfo, val progress: Float?) : UpdateState
    /** 已下好并校验通过，可以直接交给系统安装器。 */
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class DownloadFailed(val info: UpdateInfo, val message: UiText) : UpdateState
}

/**
 * 应用级的版本更新状态机。
 *
 * **为什么是个单例而不是「关于」页的 ViewModel**：54MB 的下载必须扛住「用户退出关于页」
 * 和屏幕旋转。导航目的地的 ViewModel 在返回时就被销毁了，下载会跟着被取消；而前台
 * Service 对这件事又太重（还要通知、还要权限）。挂在应用生命周期上两件事一起解决。
 *
 * 状态用 [StateFlow] 而不是 `mutableStateOf`：下载进度的回调来自 IO 线程，
 * StateFlow 的写入是线程安全的，`mutableStateOf` 不是。
 */
object AppUpdate {

    // 用 Default 而不是 Main.immediate：状态写入在任何线程都安全，而 Dispatchers.Main
    // 要在 Android 主线程 dispatcher 就位后才能用，平白多一个运行期失败模式。
    // 真正需要主线程的只有 Compose 那一侧，collect 本来就在主线程。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val repository: UpdateRepository by lazy { UpdateRepository(WeMatrixApp.instance) }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var downloadJob: Job? = null
    private var lastAutoCheckAt = 0L

    /**
     * 进「关于」页时的静默检查。
     *
     * 两道闸缺一不可：Compose 的 `LaunchedEffect(Unit)` 在每次旋转、每次回到该页都会重跑，
     * 没有它们，转个屏就是一次网络请求。**失败静默退回 [UpdateState.Idle]** —— 用户在没网的
     * 地方打开关于页，不该迎面看到一个报错。
     */
    fun autoCheck() {
        if (_state.value != UpdateState.Idle) return
        val now = System.currentTimeMillis()
        if (now - lastAutoCheckAt < AUTO_CHECK_THROTTLE_MS) return
        check(silent = true)
    }

    /**
     * 进程启动时清一次下载目录。装完新版本时应用是被系统杀掉的，那一份 54MB 的安装包
     * 就留在 filesDir 里没人管；「关于」页里的清理只在用户下次去看更新时才跑，靠不住。
     *
     * 不碰 [_state]：这里只删文件，与当前处在哪个状态无关。失败一律吞掉 —— 一个清理动作
     * 不该影响任何用户可见的东西，更不能让 `launch` 把异常抛到线程的未捕获处理器上把应用
     * 带崩（SupervisorJob 只挡住向兄弟协程传播，不会接住它）。这里用 runCatching 是安全的：
     * [scope] 与应用同生命周期、从不 cancel，它吞掉 CancellationException 的那一面碰不到。
     */
    fun sweepOnStart() {
        scope.launch { runCatching { repository.sweepStale() } }
    }

    /**
     * 检查更新。[silent] 决定失败时的表现：自动检查静默退回 [UpdateState.Idle]，
     * 手动检查停在 [UpdateState.CheckFailed] 让按钮变成「重试」并显示原因。
     *
     * 注意**不能**用「当前状态是不是 Idle」来推断这是不是自动检查 —— 用户手动点按钮时
     * 状态同样是 Idle，那样手动失败会被静默吞掉。
     */
    fun check(silent: Boolean = false) {
        if (_state.value == UpdateState.Checking) return
        // 正在下载或已下好时不去查：那会把「立即安装」打回「检查中」，用户刚等完 54MB
        // 却找不到安装按钮。
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Ready) return

        lastAutoCheckAt = System.currentTimeMillis()
        _state.value = UpdateState.Checking

        scope.launch {
            repository.check().fold(
                onSuccess = { outcome ->
                    _state.value = when (outcome) {
                        is CheckOutcome.UpToDate -> UpdateState.UpToDate
                        is CheckOutcome.Available -> {
                            // 之前下过就直接进「可安装」，不必重下 54MB
                            val existing = repository.downloadedApk(outcome.info)
                            if (existing != null) UpdateState.Ready(outcome.info, existing)
                            else UpdateState.Available(outcome.info)
                        }
                    }
                },
                onFailure = { e ->
                    _state.value = if (silent) {
                        UpdateState.Idle
                    } else {
                        UpdateState.CheckFailed(e.toUiText(R.string.err_update_check))
                    }
                }
            )
        }
    }

    /** 下载并校验。已经在 [UpdateState.Ready] 时什么都不做。 */
    fun download() {
        val info = when (val current = _state.value) {
            is UpdateState.Available -> current.info
            // 下载失败后重试：info 还在，不用回头重查一次清单
            is UpdateState.DownloadFailed -> current.info
            else -> return
        }
        if (downloadJob?.isActive == true) return

        _state.value = UpdateState.Downloading(info, null)
        downloadJob = scope.launch {
            repository.download(info) { progress ->
                // 回调来自 IO 线程。StateFlow 的写入是线程安全的，但要确认这次进度仍属于
                // 当前这一版，免得被一个已经作废的任务把进度覆盖回去。
                val now = _state.value
                if (now is UpdateState.Downloading && now.info.version == info.version) {
                    _state.value = UpdateState.Downloading(info, progress)
                }
            }.fold(
                onSuccess = { file -> _state.value = UpdateState.Ready(info, file) },
                onFailure = { e ->
                    _state.value =
                        UpdateState.DownloadFailed(info, e.toUiText(R.string.err_update_download))
                }
            )
        }
    }

    private const val AUTO_CHECK_THROTTLE_MS = 30_000L
}
