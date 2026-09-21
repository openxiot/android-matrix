package cc.openxiot.wematrix.data.api

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * 版本更新专用的 HTTP 通道（清单 + APK）。
 *
 * **刻意不复用 [RetrofitClient]**，三个理由都是硬伤：
 * 1. 它的 authInterceptor 给「除 product / ws.dtu 之外的一切主机」附上用户的
 *    `Authorization: Bearer` 与 `X-Org-Id`（RetrofitClient.kt:24-38）。拿它去请求官网
 *    www.wematrix.cc，等于把登录令牌送给站点。官网的资源是公开的，本来也不需要鉴权。
 * 2. 它的 readTimeout 是 15 秒，下载 54MB 必挂。
 * 3. 它的 HttpLoggingInterceptor 固定在 BODY 级别且无条件开启，下载 APK 时会把整个
 *    二进制响应往 logcat 里灌（还有登录请求体，那是另一个既有问题）。
 *
 * 失败一律抛 [Exception]（中文文案），由 UpdateRepository 收进 `Result`。
 */
object UpdateApi {

    private val gson = Gson()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // 30 秒是「多久没收到下一个字节」，不是「总共能花多久」。慢速但不断的下载
            // 不会被它掐掉。反过来，**千万不要**加 callTimeout —— 那个才是总时长上限，
            // 会把合法的慢速下载静默截断成失败。
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** 拉版本清单。 */
    suspend fun fetchManifest(url: String): UpdateManifest = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            // 清单在官网 CDN 后面。这个 no-cache 是说给**中间层**（CDN）听的：「别把手上
            // 那份旧清单直接给我，先回源确认」。客户端本身没装 OkHttp 的 Cache，也没有
            // 条件请求，所以不会出现这边收到 304 的情况 —— 我们每次都老实拿一份完整清单。
            // 比在 URL 上挂时间戳好：那样会冲散 CDN 的缓存键，让每次请求都落到源站。
            .header("Cache-Control", "no-cache")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("获取版本信息失败（HTTP ${response.code}）")
            }
            // OkHttp 5 起 Response.body 是非空的（4.x 才可空），不必再判一次
            val body = response.body.string()
            if (body.isBlank()) {
                throw Exception("版本信息为空")
            }
            // Gson 对着一页 HTML 或半个 JSON 会抛，对着字面量 null 会返回 null，两种都算格式不对
            val manifest = try {
                gson.fromJson(body, UpdateManifest::class.java)
            } catch (_: Exception) {
                null
            }
            manifest ?: throw Exception("版本信息格式不正确")
        }
    }

    /**
     * 把 [url] 的内容流式写进 [dest]。只管搬字节，不判断内容对不对（那是
     * UpdateRepository 的闸门）。失败时 [dest] 可能是个半截文件，由调用方清掉。
     *
     * [onProgress] 收 0f..1f；响应没带 Content-Length 时收 null —— UI 该显示不确定进度条，
     * 别编一个百分比出来。
     */
    suspend fun download(url: String, dest: File, onProgress: (Float?) -> Unit) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("下载失败（HTTP ${response.code}）")
                }
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 }

                // 空间不够就别开始写了：54MB 写一半再失败，用户等的时间白费，还可能把
                // 存储写满影响别的应用。留 1.2 倍余量，因为「可用空间」在写入期间还会缩水。
                if (total != null) {
                    val parent = dest.parentFile
                    if (parent != null && parent.usableSpace < (total * 1.2).toLong()) {
                        throw Exception("存储空间不足，安装包需要约 ${total / 1024 / 1024} MB")
                    }
                }

                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            // 协作式取消：withContext 自己不会打断阻塞中的 read()，
                            // 靠这一句在下一个 chunk 处停下（用户半路重来/退出时用得上）。
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            onProgress(total?.let { (read.toDouble() / it).toFloat().coerceIn(0f, 1f) })
                        }
                        output.flush()
                    }
                }
            }
        }
    }
}
