package cc.openxiot.wematrix.data.repository

import androidx.annotation.StringRes
import cc.openxiot.wematrix.data.AppException
import cc.openxiot.wematrix.data.api.ApiResponse
import retrofit2.Response

/**
 * 仓库层「接口失败」的统一出口。
 *
 * 改造前这段逻辑在 13 个文件里重复了 60 多遍：
 *
 * ```kotlin
 * throw Exception(response.body()?.message ?: "获取空间列表失败")
 * ```
 *
 * 中文写在仓库层就把语言钉死了（这里拿不到 Context），所以统统换成
 * `response.failWith(R.string.err_space_list)`，语言留到界面层再定。
 *
 * ⚠️ **服务端 message 优先**的语义原样保留：服务端说了原因就用它的，没有才回退到本地
 * 兜底文案。这条在 [cc.openxiot.wematrix.ui.core.toUiText] 里体现，这里只负责把它带上。
 */
internal fun <T> Response<ApiResponse<T>>.failWith(@StringRes fallback: Int, vararg args: Any): Nothing =
    throw AppException(fallback, args.toList(), body()?.message?.takeIf { it.isNotBlank() })

/**
 * 同上，但手上只有已经取出来的响应体（`val res = service.x().body()`）。
 *
 * 注意**别写成 `res?.failWith(...)`**：`res` 为空时安全调用整个式子求值为 null，异常不会
 * 抛出，函数会带着一个 null 悄悄返回。要表达「res 为空也算失败」，用 `?:` 接住它
 * （`failWith` 返回 `Nothing`，正好能当 elvis 的右操作数）。
 */
internal fun ApiResponse<*>.failWith(@StringRes fallback: Int, vararg args: Any): Nothing =
    throw AppException(fallback, args.toList(), message?.takeIf { it.isNotBlank() })

/**
 * 同上，但手上没有 [Response] —— 是「HTTP 200 但 `data` 是空的」这类。
 *
 * 这种失败服务端没给 message（给了就说明它知道自己错了，那会走 [failWith]），所以只有
 * 本地兜底文案。
 */
internal fun failWith(@StringRes fallback: Int, vararg args: Any): Nothing =
    throw AppException(fallback, args.toList())
