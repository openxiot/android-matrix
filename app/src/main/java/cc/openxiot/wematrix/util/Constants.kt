package cc.openxiot.wematrix.util

object Constants {
    const val ACCOUNT_BASE_URL = "https://account.openxiot.cn"
    const val SITE_BASE_URL = "https://matrix.openxiot.cn"
    const val PRODUCT_BASE_URL = "https://product.openxiot.cn"
    // DTU（设备网关）服务，按 IMEI 查询设备 DID
    const val DTU_BASE_URL = "https://ws.dtu.ap.openxiot.cn"
    // DTU IMEI 查询所属组织（暂时写死）
    const val DTU_ORG_ID = "yinerda"

    const val OAUTH_SCHEME = "openxiot"
    const val OAUTH_HOST = "oauth"
    const val OAUTH_PATH = "/callback"
    const val OAUTH_CALLBACK = "$OAUTH_SCHEME://$OAUTH_HOST$OAUTH_PATH"
}
