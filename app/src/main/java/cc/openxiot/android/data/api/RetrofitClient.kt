package cc.openxiot.android.data.api

import cc.openxiot.android.util.Constants
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
        val builder = request.newBuilder()
        authToken?.let {
            builder.addHeader("Authorization", "Bearer $it")
        }
        currentOrgId?.let {
            builder.addHeader("X-Org-Id", it)
        }
        builder.addHeader("Content-Type", "application/json")
        chain.proceed(builder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
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

    val siteService: SiteService by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.SITE_BASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SiteService::class.java)
    }

    val productService: ProductService by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.PRODUCT_BASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProductService::class.java)
    }
}
