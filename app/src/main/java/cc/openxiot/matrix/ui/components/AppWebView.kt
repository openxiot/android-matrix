package cc.openxiot.matrix.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import cc.openxiot.matrix.R
import cc.openxiot.matrix.ui.core.UiText

private const val TAG = "AppWebView"

/**
 * 加载第三方页面的 WebView。
 *
 * 与 web 端「跨域 iframe」那套（见《跨域加载设备页面.md》）**不是一回事**，别把契约搬过来：
 * 在 WebView 里第三方页面是**顶层文档**而不是 iframe ——
 * - 它自己的 `if (window.parent !== window)` 判定为 false，压根不会发 `iframe-height`；
 * - 没有宿主 message 监听，也没有「内外两条滚动条」要消灭，WebView 自己滚；
 * - toast 走它的**非嵌入分支**本地弹，`position: fixed` 相对 WebView 视口，看得见，不需要转发；
 * - 没有 https 外层页面，也就没有混合内容拦截（明文放行由 network_security_config 给）。
 * 所以这里**不**做任何 postMessage / JavascriptInterface 桥接。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppWebView(
    url: String,
    modifier: Modifier = Modifier,
    /**
     * 锁死竖向拖动/缩放：设备控制页是「固定高度看板」，不该被随手拖来拖去。开启时视口钉在 (0,0)，
     * 关闭 over-scroll 回弹、滚动条与捏合缩放；但**不吞触摸** —— 页面自带 JS 的顶部下拉刷新仍能
     * 收到 touchmove 正常触发（见 [LockedWebView]）。
     */
    lockScroll: Boolean = false
) {
    // WebView 实例只在 factory 里赋值、不参与重组，用普通引用即可（避免在 layout 阶段写 State）
    val ref = remember(url, lockScroll) { WebViewRef() }
    val allowedHost = remember(url) { runCatching { Uri.parse(url).host }.getOrNull() }

    var isLoading by remember(url, lockScroll) { mutableStateOf(true) }
    var hasLoadedOnce by remember(url, lockScroll) { mutableStateOf(false) }
    var canGoBack by remember(url, lockScroll) { mutableStateOf(false) }
    var error by remember(url, lockScroll) { mutableStateOf<UiText?>(null) }

    // 系统返回键先走 WebView 历史；历史走完才算「没拦住」，落到调用方的 onBack（pop 路由）。
    // 顶栏的返回箭头不接这里，它是无条件 pop —— 与其它原生页一致、可预期。
    BackHandler(enabled = canGoBack) { ref.view?.goBack() }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                (if (lockScroll) LockedWebView(ctx) else WebView(ctx)).apply {
                    settings.javaScriptEnabled = true          // 第三方页面是脚本页
                    settings.domStorageEnabled = true          // 页面用 localStorage 存状态
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    if (lockScroll) {
                        settings.setSupportZoom(false)         // 锁定时不许捏合缩放（缩放+平移=另一种「任意拖动」）
                        settings.builtInZoomControls = false
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        overScrollMode = View.OVER_SCROLL_NEVER // 触到顶端/底端不再橡皮筋回弹、不发亮
                    }

                    webViewClient = object : WebViewClient() {
                        /**
                         * 固定 host 的链接留在 WebView 内；其余（外链）交给系统浏览器 ——
                         * 否则用户会被困在一个与本应用无关的页面里，还退不出去。
                         */
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val target = request?.url ?: return false
                            if (target.host == allowedHost) return false
                            return runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, target))
                                true
                            }.getOrDefault(false)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            error = null
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            hasLoadedOnce = true
                            canGoBack = view?.canGoBack() == true
                        }

                        /** 只把主文档的失败当页面失败：子资源（图片/字体）挂了不影响页面可用 */
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            err: WebResourceError?
                        ) {
                            if (request?.isForMainFrame != true) return
                            isLoading = false
                            // WebView 自己的 description 是英文技术串，原样上屏
                            error = err?.description?.toString()?.let { UiText.Raw(it) }
                                ?: UiText.Res(R.string.err_webview_load)
                        }
                    }

                    // 只是把第三方页面的 console 打到 logcat，调试时省事；不接任何 JS 桥
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                            Log.d(TAG, "${msg.message()} @${msg.sourceId()}:${msg.lineNumber()}")
                            return true
                        }
                    }

                    loadUrl(url)
                    ref.view = this
                }
            },
            onRelease = {
                it.stopLoading()
                it.destroy()
                ref.view = null
            }
        )

        when {
            error != null -> ErrorMessage(
                message = error!!,
                onRetry = {
                    error = null
                    isLoading = true
                    hasLoadedOnce = false
                    ref.view?.reload()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )

            // 只在首帧显示进度：页内跳转时再闪一下转圈很吵
            isLoading && !hasLoadedOnce -> LoadingIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    }
}

/** WebView 实例的持有者：只在 factory 里赋值，不需要触发重组。 */
private class WebViewRef {
    var view: WebView? = null
}

/**
 * 锁死竖向拖动的 WebView。设备控制页是「固定高度看板」：内容不该被随手拖上拽下，只有页面自带 JS 的
 * 顶部下拉刷新要留着。思路是**不吞触摸**（onTouchEvent 走默认，触摸始终到达渲染器 → JS 的 touchmove
 * 照常收到、下拉刷新能触发），只把原生滚动从源头摁死，让视口永远钉在 (0,0)：
 * - `overScrollBy` 不产生超滚位移（配合外面已设的 `OVER_SCROLL_NEVER`）；
 * - `flingScroll` 不做惯性冲（拖放开手不会滑）。
 *
 * 在现代 WebView（渲染器独立合成）里内容滚动由内部合成器驱动，`scrollTo` 未必拦得住，所以才在
 * 外层再加 over-scroll 关闭 + 禁用缩放/滚动条 —— 对放得下的看板页面，Dragging 本来就无高度可滚，
 * 这层兜底只防「内容略超出视口时仍能被拖出空白」。
 */
private class LockedWebView(context: android.content.Context) : WebView(context) {
    override fun overScrollBy(
        deltaX: Int,
        deltaY: Int,
        scrollX: Int,
        scrollY: Int,
        scrollRangeX: Int,
        scrollRangeY: Int,
        maxOverScrollX: Int,
        maxOverScrollY: Int,
        isTouchEvent: Boolean
    ): Boolean = false

    override fun flingScroll(vx: Int, vy: Int) {
        // 不惯性滑动
    }

    override fun scrollTo(x: Int, y: Int) {
        // 视口永远钉在 (0,0)：任何滚动请求都回原点
        super.scrollTo(0, 0)
    }
}
