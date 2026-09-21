package cc.openxiot.wematrix

import android.app.Application
import android.content.Context
import cc.openxiot.wematrix.data.local.TokenManager

class WeMatrixApp : Application() {
    lateinit var tokenManager: TokenManager
        private set

    /**
     * 应用语言。⚠️ 这里只能读裸 prefs（见 [AppLocale.wrap]）：此刻 `onCreate` 还没跑、
     * [instance] 还是 lateinit 空值。
     *
     * 包在 Application 上而不是只包 Activity：Activity 的 baseContext 是 ActivityThread
     * 新建的 ContextImpl，**不是** Application 对象，两条链独立。两处都包，
     * `WeMatrixApp.instance.getString(...)` 才和界面是同一个语言。
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 必须早于任何 UI：AppLocale.current 是给 Compose 读的镜像
        AppLocale.init(this)
        tokenManager = TokenManager(this)
        // 清掉升级留下的旧安装包（详见 UpdateRepository.sweepStale）。异步、失败静默：
        // 它只是把 filesDir 里一份 54MB 的垃圾删掉，不该拖慢启动、更不该影响启动成败。
        AppUpdate.sweepOnStart()
    }

    companion object {
        lateinit var instance: WeMatrixApp
            private set
    }
}
