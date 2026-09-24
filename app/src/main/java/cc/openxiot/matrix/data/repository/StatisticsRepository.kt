package cc.openxiot.matrix.data.repository

import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.api.OverviewStatistics
import cc.openxiot.matrix.data.api.RetrofitClient

/**
 * 数据看板的聚合统计（只读）。
 *
 * 每个方法都要求调用方显式传 `spaceId`（当前项目**根空间**）：后端只用它校验「是该空间成员」，
 * 不参与过滤 —— 查询范围是该空间整棵子树。不在这里隐式取 TokenManager，
 * 是为了让「这是鉴权作用域」这件事在调用点看得见。
 */
class StatisticsRepository {
    private val service get() = RetrofitClient.matrixService

    /**
     * 一屏的聚合数字。
     *
     * `from` 必填（后端拒无起点的查询）；`to` 传 null = 到现在，响应里的 `to` 是实际生效的值。
     */
    suspend fun overview(spaceId: String, from: Long, to: Long?): Result<OverviewStatistics> =
        runCatching {
            val response = service.getStatisticsOverview(spaceId, from, to)
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()!!.data ?: failWith(R.string.err_stats_empty)
            } else {
                response.failWith(R.string.err_stats_get)
            }
        }
}
