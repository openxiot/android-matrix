package cc.openxiot.wematrix.i18n

import cc.openxiot.wematrix.R
import cc.openxiot.wematrix.data.AppException
import cc.openxiot.wematrix.ui.core.UiText
import cc.openxiot.wematrix.ui.core.toUiText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * `Throwable.toUiText()` 的分派规则。
 *
 * 这一层值得单独测，是因为它承载了一个容易搞反的**优先级**：服务端的 `message` 压过本地
 * 兜底文案（对应改造前 `response.body()?.message ?: "中文"` 的语义）。搞反了的表现是
 * 「服务端明明说了原因，界面却显示一句通用的本地提示」—— 不崩、不报错，只是把有用的
 * 信息丢了。
 */
class UiTextTest {

    @Test
    fun `AppException 没有服务端消息时用资源 id`() {
        assertEquals(
            UiText.Res(R.string.err_unknown),
            AppException(R.string.err_unknown).toUiText(),
        )
    }

    @Test
    fun `AppException 带参数`() {
        assertEquals(
            UiText.Res(R.string.err_unknown, listOf(500)),
            AppException(R.string.err_unknown, listOf(500)).toUiText(),
        )
    }

    @Test
    fun `服务端消息优先于本地兜底`() {
        assertEquals(
            UiText.Raw("空间已被停用"),
            AppException(R.string.err_unknown, serverMessage = "空间已被停用").toUiText(),
        )
    }

    @Test
    fun `服务端消息是空白时退回资源 id`() {
        assertEquals(
            UiText.Res(R.string.err_unknown),
            AppException(R.string.err_unknown, serverMessage = "   ").toUiText(),
        )
    }

    @Test
    fun `网络异常收成人话`() {
        // Retrofit/OkHttp 的 message 是 "Failed to connect to /10.0.0.1:443" 这种技术串，
        // 原样上屏对用户没有意义
        assertEquals(UiText.Res(R.string.err_network), IOException("Failed to connect").toUiText())
        assertEquals(UiText.Res(R.string.err_network), SocketTimeoutException("timeout").toUiText())
    }

    @Test
    fun `AppException 是 IOException 的子类时仍按 AppException 分派`() {
        // AppException 继承 Exception 而非 IOException，这条是防将来有人图省事改继承 ——
        // 一旦改了，仓库层精心挑的 resId 会被 IOException 分支吞掉，全变成「网络不可用」
        val e = AppException(R.string.err_unknown, serverMessage = "版本信息无法识别")
        assertEquals(UiText.Raw("版本信息无法识别"), e.toUiText())
    }

    @Test
    fun `未知异常保留原消息`() {
        assertEquals(UiText.Raw("boom"), RuntimeException("boom").toUiText())
    }

    @Test
    fun `既无消息也非网络异常时给通用兜底`() {
        assertEquals(UiText.Res(R.string.err_unknown), RuntimeException().toUiText())
        assertEquals(UiText.Res(R.string.err_unknown), RuntimeException("  ").toUiText())
    }
}
