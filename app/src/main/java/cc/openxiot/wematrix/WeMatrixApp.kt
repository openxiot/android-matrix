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
    }

    companion object {
        lateinit var instance: WeMatrixApp
            private set
    }
}
