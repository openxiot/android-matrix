package cc.openxiot.wematrix.data

import androidx.annotation.StringRes

/**
 * 一条「还没定语言的用户可见错误」。
 *
 * 为什么不是 `Exception("中文")`：仓库层是被 ViewModel 直接 new 出来的，手里只有
 * Retrofit 和 Result，**拿不到 Context**，硬编码中文就把语言钉死了。这里只带资源 id
 * 和参数，到界面层才用 `Throwable.toUiText()` 定语言。
 *
 * [serverMessage] 是服务端 `message` 字段的原文（服务端不下发英文，属已知局限）：
 * 有就优先显示，没有才回退 [resId] —— 正好是原来 `?.message ?: "中文"` 的优先级。
 *
 * [message] 只给日志看，**界面层禁止读它**，一律走 `toUiText()`。
 */
class AppException(
    @StringRes val resId: Int,
    val args: List<Any> = emptyList(),
    val serverMessage: String? = null,
) : Exception(serverMessage)
