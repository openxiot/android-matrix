package cc.openxiot.wematrix.data.repository

import cc.openxiot.wematrix.data.api.MobileCatalog
import cc.openxiot.wematrix.data.api.RetrofitClient

/**
 * 看板编辑器要的候选清单（复用 web 的 `/dashboard/web/catalog`）。
 *
 * 存在这个仓库只有一个理由：编辑器做多级级联要**实体本身的形状** ——
 * 服务卡要 `functions[].response` 里的 field/unit/valueList/bitList，设备卡要设备型号。
 * 这些在别处都拿不全（空间图里只有 [cc.openxiot.wematrix.data.api.ModbusServiceBrief]，无 functions；
 * 设备列表只有 did/type/online）。
 *
 * 它是**进编辑态时取一次**的一次性数据，不是每次刷新都拉，所以整份下发、不做投影裁剪。
 */
class MobileDashboardCatalogRepository {
    private val service get() = RetrofitClient.matrixService

    suspend fun catalog(spaceId: String): Result<MobileCatalog> = runCatching {
        val r = service.getDashboardCatalog(spaceId)
        if (r.isSuccessful && r.body()?.success == true) {
            r.body()!!.data ?: throw Exception("候选清单为空")
        } else {
            throw Exception(r.body()?.message ?: "获取候选清单失败")
        }
    }
}