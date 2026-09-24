package cc.openxiot.matrix.ui.login

import android.content.Context
import com.tencent.mm.opensdk.modelmsg.SendAuth
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 微信登录入口。
 *
 * 流程:login() 拉起微信授权 → 结果经 wxapi/WXEntryActivity 回调到 handleAuthResult()
 * → 写入 authResult,由登录页 collect 后拿 code 去换 token。
 */
object Weixin {

    const val APP_ID = "wx1f1617264d7c7c34"
    const val SCOPE = "snsapi_userinfo" // 只能填 snsapi_userinfo

    // WXEntryActivity 收到授权结果后写入,登录页通过 collect 消费
    private val _authResult = MutableStateFlow<SendAuth.Resp?>(null)
    val authResult: StateFlow<SendAuth.Resp?> = _authResult.asStateFlow()

    // 本次登录请求生成的随机 state,回调时校验防 CSRF
    private var lastState = ""

    /**
     * 拉起微信授权。
     * @return true 表示已成功拉起微信;false 表示未安装微信或微信版本过低。
     */
    fun login(context: Context): Boolean {
        // checkSignature=false:跳过对"微信 App 自身签名"的本地校验,便于在非官方/国际版微信上联调。
        // 生产环境建议改回 true。
        val api: IWXAPI = WXAPIFactory.createWXAPI(context, APP_ID, false)
        api.registerApp(APP_ID)

        lastState = UUID.randomUUID().toString()

        val req = SendAuth.Req()
        req.scope = SCOPE
        req.state = lastState
        return api.sendReq(req)
    }

    /** 由 wxapi/WXEntryActivity 在 onResp 中调用,校验 state 后写入结果。 */
    fun handleAuthResult(resp: SendAuth.Resp) {
        if (resp.state != lastState) return // state 不匹配,丢弃
        _authResult.value = resp
    }

    /** 消费完结果后重置,避免旧 code 被重复处理。 */
    fun clearAuthResult() {
        _authResult.value = null
    }
}
