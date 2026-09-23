package cc.openxiot.wematrix.data.repository

import cc.openxiot.wematrix.R
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
            response.body()!!.data ?: failWith(R.string.err_modbus_service_missing)
        } else {
            response.failWith(R.string.err_modbus_service_get)
        }
    }

    /** 某设备下挂的全部服务 */
    suspend fun listByDevice(spaceId: String, did: String): Result<List<ModbusService>> = runCatching {
        val response = service.getModbusServicesByDevice(spaceId, did)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: emptyList()
        } else {
            response.failWith(R.string.err_modbus_service_list)
        }
    }

    /**
     * 调用一个方法。返回「字段名 → 值」；写方法返回空表。
     *
     * [values] 只在写方法上用（读方法传了后端会拒），缺省 null = 不传；后端对未给值的
     * 字段回落到定义里的缺省值。移动端暂无填值入口，写方法调用暂以缺省值下发。
     *
     * 失败信息是后端原样下发的设备侧描述（如 `no response from parent device`、
     * 父设备错误描述），调用方直接展示即可。
     */
    suspend fun invoke(
        spaceId: String,
        serviceId: String,
        functionIndex: Int,
        values: Map<String, Any?>? = null
    ): Result<Map<String, Any?>> = runCatching {
        val response = service.invokeModbusService(
            spaceId,
            InvokeModbusServiceRequest(
                service = serviceId,
                function = functionIndex,
                values = if (values.isNullOrEmpty()) null else values
            )
        )
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: emptyMap()
        } else {
            response.failWith(R.string.err_modbus_service_invoke)
        }
    }
}
