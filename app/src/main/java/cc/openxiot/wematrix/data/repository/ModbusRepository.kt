package cc.openxiot.wematrix.data.repository

import cc.openxiot.wematrix.data.api.ModbusConfig
import cc.openxiot.wematrix.data.api.RetrofitClient

/**
 * 设备点表（只读）。
 *
 * 只有查询——新建/编辑/删除/生命周期流转在移动端不做，故对应的写接口也没有包装。
 */
class ModbusRepository {
    private val service get() = RetrofitClient.matrixService

    /** 当前账号可见的全部设备点表：本组织私有 + 各组织公开 */
    suspend fun getVisibleConfigs(): Result<List<ModbusConfig>> = runCatching {
        val response = service.getVisibleModbusConfigs()
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: emptyList()
        } else {
            throw Exception(response.body()?.message ?: "获取设备点表失败")
        }
    }

    /** 全部公开设备点表：未选组织（请求头里没有 X-Org-Id）时 /visible 可能被拒，用它兜底 */
    suspend fun getPublicConfigs(): Result<List<ModbusConfig>> = runCatching {
        val response = service.getPublicModbusConfigs()
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: emptyList()
        } else {
            throw Exception(response.body()?.message ?: "获取公开设备点表失败")
        }
    }

    /** 查询单条设备点表 */
    suspend fun getConfig(id: String): Result<ModbusConfig> = runCatching {
        val response = service.getModbusConfig(id)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: throw Exception("设备点表不存在")
        } else {
            throw Exception(response.body()?.message ?: "获取设备点表失败")
        }
    }
}
