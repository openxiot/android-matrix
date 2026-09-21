package cc.openxiot.wematrix.data.repository

import cc.openxiot.wematrix.R
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
            response.failWith(R.string.err_device_list)
        }
    }

    suspend fun addDevices(spaceId: String, devices: List<DeviceRegistration>): Result<Unit> = runCatching {
        val response = service.addDevices(spaceId, devices)
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            response.failWith(R.string.err_device_add)
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
            response.failWith(R.string.err_device_move)
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
        // body() 为空（网关没按约定回 JSON）与 success=false 是同一句文案，这里先接住前者
        val res = dtuService.getDidByImei(orgId, imei).body()
            ?: failWith(R.string.err_device_did_lookup)
        if (res.success == true) {
            res.data ?: failWith(R.string.err_device_imei_not_found, imei)
        } else {
            res.failWith(R.string.err_device_did_lookup)
        }
    }

    /** 扫码/IMEI 添加设备的底层逻辑：以 key-value 表登记设备 */
    private suspend fun addDeviceByBody(spaceId: String, body: Map<String, String>): Result<Unit> = runCatching {
        val response = service.addDeviceByQr(spaceId, body)
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            response.failWith(R.string.err_device_add)
        }
    }
}
