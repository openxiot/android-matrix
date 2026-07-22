package cc.openxiot.android.data.repository

import cc.openxiot.android.data.api.DeviceEntity
import cc.openxiot.android.data.api.DeviceRegistration
import cc.openxiot.android.data.api.RetrofitClient

class DeviceRepository {
    private val service get() = RetrofitClient.siteService

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

    suspend fun addDeviceByQr(spaceId: String, qrContent: String): Result<Unit> = runCatching {
        val response = service.addDeviceByQr(spaceId, qrContent)
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            throw Exception(response.body()?.message ?: "添加设备失败")
        }
    }
}
