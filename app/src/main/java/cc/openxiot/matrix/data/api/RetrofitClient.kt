package cc.openxiot.matrix.data.api

import cc.openxiot.matrix.util.Constants
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private var authToken: String? = null
    private var currentOrgId: String? = null

    fun setToken(token: String?) {
        authToken = token
    }

    fun setOrgId(orgId: String?) {
        currentOrgId = orgId
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        // 公开接口（product 的公开接口、DTU 的 IMEI 查询）不附加登录态
        val isPublicHost = request.url.host == "product.openxiot.cn" || request.url.host == "ws.dtu.ap.openxiot.cn"
        val builder = request.newBuilder()
        if (!isPublicHost) {
            authToken?.let {
                builder.addHeader("Authorization", "Bearer $it")
            }
            currentOrgId?.let {
                builder.addHeader("X-Org-Id", it)
            }
        }
        chain.proceed(builder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val accountService: AccountService by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.ACCOUNT_BASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AccountService::class.java)
    }

    val matrixService: MatrixService by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.SITE_BASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MatrixService::class.java)
    }

    val productService: ProductService by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.PRODUCT_BASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProductService::class.java)
    }

    val dtuService: DtuService by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.DTU_BASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DtuService::class.java)
    }
}
