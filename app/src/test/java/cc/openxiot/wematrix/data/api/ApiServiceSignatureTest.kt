package cc.openxiot.wematrix.data.api

import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * 四个 Retrofit 接口的**方法签名**回归：`create()` 时就把每个方法解析一遍，解析不过就抛。
 *
 * **为什么值得单独一个测试**：Retrofit 对方法签名是**懒校验**的 —— `create()` 一声不吭，
 * 等第一次真的调用那个方法才抛。于是「参数类型里带了通配符或类型变量」这类错编译期看不见、
 * 跑一遍现有的纯函数单测也看不见，要等用户界面上点了那颗按钮才炸。告警页的「处理」就这么炸过：
 *
 * ```
 * Parameter type must not include a type variable or wildcard:
 *   java.util.Map<java.lang.String, ?> (parameter #3) for method MatrixService.handleModbusAlarm
 * ```
 *
 * 病根在 Kotlin 的 Java 签名生成：泛型实参非 final 时会带上通配符
 * （`@Body body: Map<String, Any?>` 编出来是 `Map<String, ? extends Object>`），
 * 而 Retrofit 拒绝参数类型里出现通配符与类型变量。修法是给实参加 `@JvmSuppressWildcards`
 * （见 `ApiService.kt` 的文件头）。这个错**不会**在编译或 lint 里露头，只能靠这里钉住 ——
 * 它第一次跑就一并揪出了两处同一病因的老声明（`setDeviceProperties` / `invokeDeviceAction`）。
 *
 * 新增接口方法不必改这个测试：它按接口整体解析，新方法自动被覆盖。
 *
 * `baseUrl` 是占位地址：`validateEagerly` 只解析签名，不发请求，故随便给个合法 URL 就行。
 */
class ApiServiceSignatureTest {

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://example.invalid/")
        .addConverterFactory(GsonConverterFactory.create())
        // 不给 client：用 Retrofit 自带的 OkHttpClient（纯 JVM，单测里起得来）
        .validateEagerly(true)
        .build()

    @Test
    fun accountService() {
        retrofit.create(AccountService::class.java)
    }

    @Test
    fun matrixService() {
        retrofit.create(MatrixService::class.java)
    }

    @Test
    fun productService() {
        retrofit.create(ProductService::class.java)
    }

    @Test
    fun dtuService() {
        retrofit.create(DtuService::class.java)
    }
}
