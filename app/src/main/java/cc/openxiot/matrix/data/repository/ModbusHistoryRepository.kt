package cc.openxiot.matrix.data.repository

import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.api.ModbusHistoryCurrent
import cc.openxiot.matrix.data.api.ModbusHistoryFailures
import cc.openxiot.matrix.data.api.ModbusHistoryRange
import cc.openxiot.matrix.data.api.RetrofitClient

/**
 * Modbus 采集历史（只读）。
 *
 * 三个接口的分工：当前值快照 / 单字段序列 / 失败清单。取数必须自己算 from/to
 * —— 接口没有「最近 N 分钟」这类窗口参数。
 *
 * `spaceId` 一律是当前项目**根空间**（鉴权作用域，查询范围是它整棵子树）。
 */
class ModbusHistoryRepository {
    private val service get() = RetrofitClient.matrixService

    /** 某服务的当前值快照（每个方法最后一次成功采到的字段值） */
    suspend fun current(spaceId: String, serviceId: String): Result<ModbusHistoryCurrent> =
        runCatching {
            val response = service.getHistoryCurrent(spaceId, serviceId)
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()!!.data ?: failWith(R.string.err_sampling_status_empty)
            } else {
                response.failWith(R.string.err_sampling_status)
            }
        }

    /**
     * 一个方法的某一个字段在 [from, to] 内的序列。
     *
     * `to` 传 null = 现在；`maxPoints` 传 null = 后端缺省 500。
     * 窗口内原始样本超过 2 万条时后端报错要求收窄，故调用方要把它当失败展示、不要静默吞掉。
     */
    suspend fun range(
        spaceId: String,
        serviceId: String,
        functionIndex: Int,
        field: String,
        from: Long,
        to: Long?,
        maxPoints: Int? = null
    ): Result<ModbusHistoryRange> = runCatching {
        val response = service.getHistoryRange(
            spaceId = spaceId,
            serviceId = serviceId,
            functionIndex = functionIndex,
            field = field,
            from = from,
            to = to,
            maxPoints = maxPoints
        )
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: failWith(R.string.err_sampling_data_empty)
        } else {
            response.failWith(R.string.err_sampling_data)
        }
    }

    /**
     * 采集失败清单 + 汇总，按时间倒序。
     *
     * `serviceId` 传 null = **整个空间（含子空间）**：项目级页面用这一条顶掉「按服务扇出的 N 条」，
     * 而不是拿 N 个响应在内存里拼。`limit` 传 null = 后端缺省 200。
     */
    suspend fun failures(
        spaceId: String,
        from: Long,
        to: Long?,
        serviceId: String? = null,
        functionIndex: Int? = null,
        type: String? = null,
        limit: Int? = null
    ): Result<ModbusHistoryFailures> = runCatching {
        val response = service.getHistoryFailures(
            spaceId = spaceId,
            from = from,
            to = to,
            serviceId = serviceId,
            functionIndex = functionIndex,
            type = type,
            limit = limit
        )
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: failWith(R.string.err_sampling_fault_empty)
        } else {
            response.failWith(R.string.err_sampling_fault)
        }
    }
}
