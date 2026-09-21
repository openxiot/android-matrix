package cc.openxiot.wematrix.ui.core

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import cc.openxiot.wematrix.R
import cc.openxiot.wematrix.data.AppException
import java.io.IOException

/**
 * 一条「还没定语言的界面文案」。
 *
 * ViewModel、`AppUpdate` 这些拿不到 Context 的地方存它，渲染时才定语言 —— 于是
 * **已经在状态里**的文案，在切语言 + `recreate()` 之后也跟着变，而不是被钉在出错那一刻
 * 的语言上。ViewModel 跨 `recreate()` 存活，存 `String` 就正好会钉住：切完语言，屏幕上
 * 那条错误还是旧语言。这个类型就是为这一条存在的。
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Quantity(
        @PluralsRes val id: Int,
        val quantity: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    /** 已定型的原文：服务端下发的 `message`、未知异常的 `message`。 */
    data class Raw(val text: String) : UiText
}

/**
 * [fallback] 是「什么消息都没有」时的兜底。默认一句通用的；调用点若知道自己在做什么
 * （比如更新检查），传一条更具体的，比通用的「出了点问题」有用。
 */
fun Throwable.toUiText(@StringRes fallback: Int = R.string.err_unknown): UiText = when (this) {
    is AppException ->
        serverMessage?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) }
            ?: UiText.Res(resId, args)
    // Retrofit/OkHttp 的网络异常 message 是英文技术串（"Failed to connect to /10.0.0.1:443"），
    // 收成一句人话比原样上屏有用。
    is IOException -> UiText.Res(R.string.err_network)
    else ->
        message?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) }
            ?: UiText.Res(fallback)
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> stringResource(id, *args.resolved(LocalContext.current))
    is UiText.Quantity -> pluralStringResource(id, quantity, *args.resolved(LocalContext.current))
}

/** Toast 等非组合场景。传带应用语言的 context（`LocalContext.current`），别传 applicationContext。 */
fun UiText.asString(context: Context): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> context.getString(id, *args.resolved(context))
    is UiText.Quantity ->
        context.resources.getQuantityString(id, quantity, *args.resolved(context))
}

/** 允许参数里再嵌一条 UiText：`"添加设备失败: %1$s"` 的 `%1$s` 本身可能是一条 UiText。 */
private fun List<Any>.resolved(context: Context): Array<Any> =
    map { if (it is UiText) it.asString(context) else it }.toTypedArray()
