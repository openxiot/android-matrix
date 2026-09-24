package cc.openxiot.matrix.util

import cc.openxiot.matrix.BuildConfig

object Constants {
    const val ACCOUNT_BASE_URL = "https://account.openxiot.cn"
    const val SITE_BASE_URL = "https://matrix.openxiot.cn"
    const val PRODUCT_BASE_URL = "https://product.openxiot.cn"
    // DTU（设备网关）服务，按 IMEI 查询设备 DID
    const val DTU_BASE_URL = "https://ws.dtu.ap.openxiot.cn"
    // DTU IMEI 查询所属组织（暂时写死）
    const val DTU_ORG_ID = "yinerda"

    // 设备操作页：第三方平板端页面（暂时写死，全部设备共用；将来按型号映射时换成映射表）。
    // 走的是明文 http，靠 network_security_config 里放行的 192.168.5.80 才加载得出来。
    const val DEVICE_OPERATION_URL = "http://192.168.5.62:8000/air-conditioner-tablet.html"

    // 版本更新清单。这是本文件里唯一不能写成 const 的 URL —— 它按构建类型取值
    // （debug 与 release 域名不同），值在 app/build.gradle.kts 的 buildConfigField 里。
    // 官网侧的文件是 webapp-matrix-site 仓库的 static/data/apps/android.json，
    // 由 android-matrix 打 tag 发版时自动改写。
    val UPDATE_MANIFEST_URL: String get() = BuildConfig.UPDATE_MANIFEST_URL

    const val OAUTH_SCHEME = "openxiot"
    const val OAUTH_HOST = "oauth"
    const val OAUTH_PATH = "/callback"
    const val OAUTH_CALLBACK = "$OAUTH_SCHEME://$OAUTH_HOST$OAUTH_PATH"
}
