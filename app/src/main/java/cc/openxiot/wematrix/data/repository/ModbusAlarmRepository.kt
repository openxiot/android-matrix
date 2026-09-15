package cc.openxiot.wematrix.data.repository

import cc.openxiot.wematrix.data.api.ModbusAlarm
import cc.openxiot.wematrix.data.api.ModbusAlarmList
import cc.openxiot.wematrix.data.api.ModbusAlarmQuery
import cc.openxiot.wematrix.data.api.RetrofitClient

/**
 * Modbus 阈值告警（查询 + 处理）。
 *
 * 「处理」是本端唯一的新增写操作 —— 它是 web 已有的幂等接口（重复点击后端当成功返回），
 * 写的是告警回执，不改任何点表或服务定义。点表 / 服务的增删改在移动端本来就不做。
 *
 * `spaceId` 一律是当前项目**根空间**（鉴权作用域，查询范围是它整棵子树）。
 */
class ModbusAlarmRepository {
    private val service get() = RetrofitClient.matrixService

    /**
     * 告警清单 + 汇总。
     *
     * `query.serviceId` 不传 = **整个空间（含子空间）**，清单里混着多个服务的告警，
     * 靠每条 item 自带的 `serviceId` 认领归属。`from` 必填，`to` 传 null = 到现在。
     *
     * 三态筛选（`open` / `handled`）原样透传：null 会被 Retrofit 从 URL 里省掉，
     * 正是「不限」的语义。
     */
    suspend fun list(
        spaceId: String,
        from: Long,
        to: Long?,
        query: ModbusAlarmQuery = ModbusAlarmQuery()
    ): Result<ModbusAlarmList> = runCatching {
        val response = service.getModbusAlarms(
            spaceId = spaceId,
            from = from,
            to = to,
            serviceId = query.serviceId,
            functionIndex = query.functionIndex,
            field = query.field,
            level = query.level,
            openState = query.open,
            handled = query.handled,
            limit = query.limit
        )
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: throw Exception("告警数据为空")
        } else {
            throw Exception(response.body()?.message ?: "获取告警清单失败")
        }
    }

    /** 处理一条告警：返回**更新后的那一条**，页面据此就地替换该行、不整页刷新 */
    suspend fun handle(spaceId: String, id: String): Result<ModbusAlarm> = runCatching {
        // 空对象：后端这个接口不看 body（照 web 传 `{}`；类型上的 @JvmSuppressWildcards 见 ApiService 的文件头）
        val response = service.handleModbusAlarm(spaceId, id, emptyMap())
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: throw Exception("告警不存在")
        } else {
            throw Exception(response.body()?.message ?: "处理告警失败")
        }
    }
}
