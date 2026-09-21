package cc.openxiot.wematrix

import android.app.Application
import cc.openxiot.wematrix.data.local.TokenManager

class WeMatrixApp : Application() {
    lateinit var tokenManager: TokenManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
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
