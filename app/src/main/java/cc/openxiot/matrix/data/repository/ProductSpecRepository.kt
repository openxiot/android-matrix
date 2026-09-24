package cc.openxiot.matrix.data.repository

import cc.openxiot.matrix.data.api.ProductService
import cc.openxiot.matrix.data.api.RetrofitClient

/**
 * 设备卡编辑器要的属性表：从产品规格（`GET /product/instance/one/{type}`）里摊平出来。
 *
 * 为什么单独拉一次（镜像 web 的 `DeviceSpecService`）：设备卡的数据里只有 `pid` 与 `type`，
 * 属性名与单位都在产品规格里 —— 规格是前端本来就在用的东西（设备页也查它），matrix 手里
 * 只有 `DeviceEntity.type`，服务端顺带下发 `unit` 那条路在设备卡上走不通。
 *
 * **一个型号只取一次**（[asked]），失败静默 —— 查不到规格就退回 pid 原文，那是诚实的降级。
 *
 * 线上形状（与 web `DeviceSpecService.specProperties` 对齐）：
 * ```
 * data.services = { <siid>: { iid, properties: { <piid>: { iid, unit, description: { lang: 名 } } } } }
 * ```
 * 描述按语言存着，本端单语言，读 `zh-CN`，没有就退回任一语言。
 */
class ProductSpecRepository {
    private val product: ProductService by lazy { RetrofitClient.productService }
    private val specs = HashMap<String, List<DeviceProperty>>()
    private val asked = HashSet<String>()

    /** 一个设备属性（展示层口径）：名字是产品规格的文案，随语言变；单位原样显示。 */
    data class DeviceProperty(val siid: Int, val piid: Int, val name: String, val unit: String)

    /** 取一个型号的规格（幂等）。失败静默，属性表就一直是空的 —— 卡片退回 pid 原文。 */
    suspend fun load(type: String) {
        if (type.isBlank() || type in asked) return
        asked += type
        runCatching {
            val r = product.getProductInstance(type)
            if (r.isSuccessful && r.body()?.success == true) {
                parse(r.body()!!.data)
            } else {
                emptyList()
            }
        }.onSuccess { props -> specs[type] = props }
    }

    /** 这个型号的属性表（已到的部分；没取过 / 还没到 / 失败都给空）。 */
    fun propertiesOf(type: String?): List<DeviceProperty> =
        type?.let { specs[it] } ?: emptyList()

    /** 从 pid 切出 `(siid, piid)`：**按 did 前缀切、不按 `.` 分段**（did 可能带点）。 */
    fun splitPid(did: String?, pid: String?): Pair<Int, Int>? {
        if (did.isNullOrEmpty() || pid == null || !pid.startsWith("$did.")) return null
        val rest = pid.removePrefix("$did.")
        val dot = rest.indexOf('.')
        if (dot <= 0) return null
        val siid = rest.substring(0, dot).toIntOrNull() ?: return null
        val piid = rest.substring(dot + 1).toIntOrNull() ?: return null
        return siid to piid
    }

    /** 按 `(siid, piid)` 找属性的展示名；没有 → `null`（调用方退回 pid 原文）。 */
    fun propertyName(type: String?, did: String?, pid: String?): String? {
        if (type == null) return null
        val id = splitPid(did, pid) ?: return null
        val name = propertiesOf(type).firstOrNull { it.siid == id.first && it.piid == id.second }?.name
        return name?.takeIf { it.isNotBlank() }
    }

    private fun parse(data: Map<String, Any?>?): List<DeviceProperty> {
        val out = ArrayList<DeviceProperty>()
        val services = data?.get("services") as? Map<*, *> ?: return out
        for (svc in services.values) {
            if (svc !is Map<*, *>) continue
            val siid = (svc["iid"] as? Number)?.toInt() ?: continue
            val properties = svc["properties"] as? Map<*, *> ?: continue
            for (prop in properties.values) {
                if (prop !is Map<*, *>) continue
                val piid = (prop["iid"] as? Number)?.toInt() ?: continue
                val unit = prop["unit"] as? String ?: ""
                val name = localeName(prop["description"] as? Map<*, *>)
                out += DeviceProperty(siid, piid, name, unit)
            }
        }
        return out
    }

    private fun localeName(description: Map<*, *>?): String {
        if (description.isNullOrEmpty()) return ""
        (description["zh-CN"] as? String)?.let { if (it.isNotBlank()) return it }
        // 没有中文就退回第一个非空语言
        return description.values.asSequence().mapNotNull { it as? String }
            .firstOrNull { it.isNotBlank() } ?: ""
    }
}