package cc.openxiot.wematrix.util

object Constants {
    const val ACCOUNT_BASE_URL = "https://account.openxiot.cn"
    const val SITE_BASE_URL = "https://site.openxiot.cn"
    const val PRODUCT_BASE_URL = "https://product.openxiot.cn"

    const val OAUTH_SCHEME = "openxiot"
    const val OAUTH_HOST = "oauth"
    const val OAUTH_PATH = "/callback"
    const val OAUTH_CALLBACK = "$OAUTH_SCHEME://$OAUTH_HOST$OAUTH_PATH"
}
