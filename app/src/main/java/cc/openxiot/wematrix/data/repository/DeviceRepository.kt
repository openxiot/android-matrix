package cc.openxiot.wematrix.data.repository

import cc.openxiot.wematrix.data.api.DeviceEntity
import cc.openxiot.wematrix.data.api.DeviceRegistration
import cc.openxiot.wematrix.data.api.MoveDeviceRequest
import cc.openxiot.wematrix.data.api.RetrofitClient

class DeviceRepository {
    private val service get() = RetrofitClient.matrixService
    private val dtuService get() = RetrofitClient.dtuService

    suspend fun getDevices(spaceId: String): Result<List<DeviceEntity>> = runCatching {
        val response = service.getDevices(spaceId)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: emptyList()
        } else {
            throw Exception(response.body()?.message ?: "获取设备列表失败")
        }
    }

    suspend fun addDevices(spaceId: String, devices: List<DeviceRegistration>): Result<Unit> = runCatching {
        val response = service.addDevices(spaceId, devices)
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            throw Exception(response.body()?.message ?: "添加设备失败")
        }
    }

    suspend fun moveDevice(spaceId: String, rootSpaceId: String, did: String): Result<Unit> = runCatching {
        val body = MoveDeviceRequest(
            spaceId = spaceId,
            rootSpaceId = rootSpaceId,
            dids = listOf(did)
        )
        val response = service.updateDeviceSpace(body)
        if (!response.isSuccessful || response.body()?.success != true) {
            throw Exception(response.body()?.message ?: "移动设备失败")
        }
    }

    /** 扫码添加设备：二维码内容形如 "key:value,key:value"，逐段解析为 map 后登记 */
    suspend fun addDeviceByQr(spaceId: String, qrContent: String): Result<Unit> = runCatching {
        val body = qrContent.split(",").mapNotNull { part ->
            val kv = part.split(":", limit = 2)
            if (kv.size == 2) kv[0].trim() to kv[1].trim() else null
        }.toMap()
        addDeviceByBody(spaceId, body)
    }

    /**
     * 按 IMEI 添加 DTU 设备（特例）：先经 DTU 网关按 IMEI 查询 DID，
     * 再以 did + key 登记设备，其中设备的 key（访问密钥/AccessKey）取 IMEI 的值。
     * 后端（matrix device/one）只消费 did、key 两个字段。
     */
    suspend fun addDeviceByImei(spaceId: String, imei: String, orgId: String): Result<Unit> {
        val did = getDidByImei(orgId, imei).getOrElse { return Result.failure(it) }
        return addDeviceByBody(spaceId, mapOf("did" to did, "key" to imei))
    }

    /** 根据 IMEI 从 DTU 网关查询 DID */
    suspend fun getDidByImei(orgId: String, imei: String): Result<String> = runCatching {
        val res = dtuService.getDidByImei(orgId, imei).body()
        if (res?.success == true) {
            res.data ?: throw Exception("未查询到该 IMEI（$imei）对应的设备")
        } else {
            throw Exception(res?.message ?: "根据 IMEI 查询 DID 失败")
        }
    }

    /** 扫码/IMEI 添加设备的底层逻辑：以 key-value 表登记设备 */
    private suspend fun addDeviceByBody(spaceId: String, body: Map<String, String>): Result<Unit> = runCatching {
        val response = service.addDeviceByQr(spaceId, body)
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            throw Exception(response.body()?.message ?: "添加设备失败")
        }
    }
}
