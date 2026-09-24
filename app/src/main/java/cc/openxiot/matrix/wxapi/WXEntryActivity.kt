package cc.openxiot.matrix.wxapi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.tencent.mm.opensdk.modelbase.BaseReq
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.modelmsg.SendAuth
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import cc.openxiot.matrix.ui.login.Weixin

/**
 * 微信 SDK 回调入口。必须放在包 cc.openxiot.matrix.wxapi 下,并在 Manifest 中
 * 注册 scheme 为 wx{appId} 的 intent-filter,微信才会把授权结果投递到这里。
 */
class WXEntryActivity : Activity(), IWXAPIEventHandler {

    private lateinit var api: IWXAPI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = WXAPIFactory.createWXAPI(this, Weixin.APP_ID, true)
        api.handleIntent(intent, this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        api.handleIntent(intent, this)
    }

    override fun onReq(req: BaseReq) {
        // 微信发起的请求,登录场景一般用不到
    }

    override fun onResp(resp: BaseResp) {
        when (resp) {
            is SendAuth.Resp -> Weixin.handleAuthResult(resp)
            else -> { /* 其他类型响应,暂不处理 */ }
        }
        finish()
    }
}
