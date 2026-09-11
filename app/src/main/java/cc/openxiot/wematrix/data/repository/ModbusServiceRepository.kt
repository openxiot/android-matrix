package cc.openxiot.wematrix.data.repository

import cc.openxiot.wematrix.data.api.InvokeModbusServiceRequest
import cc.openxiot.wematrix.data.api.ModbusService
import cc.openxiot.wematrix.data.api.RetrofitClient

/**
 * Modbus 服务（只读 + 调用）。
 *
 * 只有查询与 invoke——新建/编辑/删除服务需要空间管理员，移动端不做，故对应的写接口也没有包装。
 *
 * 每个方法都要求调用方显式传 `spaceId`（当前项目**根空间**）：后端只用它校验「是该空间成员」，
 * 不参与过滤。不在这里隐式取 TokenManager，是为了让「这是鉴权作用域」这件事在调用点看得见。
 */
class ModbusServiceRepository {
    private val service get() = RetrofitClient.matrixService

    /** 查询单条服务（完整定义） */
    suspend fun getService(spaceId: String, id: String): Result<ModbusService> = runCatching {
        val response = service.getModbusService(spaceId, id)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: throw Exception("服务不存在")
        } else {
            throw Exception(response.body()?.message ?: "获取服务失败")
        }
    }

    /** 某设备下挂的全部服务 */
    suspend fun listByDevice(spaceId: String, did: String): Result<List<ModbusService>> = runCatching {
        val response = service.getModbusServicesByDevice(spaceId, did)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: emptyList()
        } else {
            throw Exception(response.body()?.message ?: "获取服务列表失败")
        }
    }

    /**
     * 调用一个方法。返回「字段名 → 值」；写方法返回空表。
     *
     * 失败信息是后端原样下发的设备侧描述（如 `no response from parent device`、
     * 父设备错误描述），调用方直接展示即可。
     */
    suspend fun invoke(
        spaceId: String,
        serviceId: String,
        functionIndex: Int
    ): Result<Map<String, Any?>> = runCatching {
        val response = service.invokeModbusService(
            spaceId,
            InvokeModbusServiceRequest(service = serviceId, function = functionIndex)
        )
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: emptyMap()
        } else {
            throw Exception(response.body()?.message ?: "调用失败")
        }
    }
}
