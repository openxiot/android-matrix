package cc.openxiot.android

import android.app.Application
import cc.openxiot.android.data.local.TokenManager

class OpenXiotApp : Application() {
    lateinit var tokenManager: TokenManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        tokenManager = TokenManager(this)
    }

    companion object {
        lateinit var instance: OpenXiotApp
            private set
    }
}
