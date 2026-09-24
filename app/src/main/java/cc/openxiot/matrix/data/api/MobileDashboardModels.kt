package cc.openxiot.matrix.data.api

import com.google.gson.annotations.SerializedName

/**
 * 移动端看板（可自定义首页）的线上契约，对应后端 `service-matrix` 的
 * `/matrix/v1/dashboard/mobile` 与复用的 `/matrix/v1/dashboard/web/catalog`。
 *
 * 布局 = 一张卡的**有序数组**（数组顺序 = 阅读顺序，竖屏自上而下），卡片尺寸只有
 * `FULL` / `HALF` 两档（HALF 仅 stat 与 service 卡可用）—— 没有 web 那种 24 列网格的 x/y，
 * 但半宽卡记自己**占哪半格**（`side`），见 [MobileDashboardWidget.side]。
 * **config 语义与 web 逐字同构**（服务端共享渲染核心 + 同一校验器），
 * 所以各类型卡片的 config 键在这里不另造一套。
 *
 * 展示用的 `creator` / `updater` 本端不读（无界面展示处），模型里就不声明 —— Gson 忽略未知键。
 */

/** 布局写入/读取的实体：`spaceId` 是鉴权作用域（当前项目根空间），`version` 是乐观锁。 */
data class MobileDashboardLayout(
    @SerializedName("spaceId") val spaceId: String? = null,
    /** 乐观锁：从未保存的后端返回 0；保存时把它原样带上，冲突由服务端 CAS 拒绝。 */
    @SerializedName("version") val version: Long? = null,
    /** 卡片有序数组（顺序 = 展示顺序） */
    @SerializedName("widgets") val widgets: List<MobileDashboardWidget> = emptyList()
)

/** 布局里的一张卡。 */
data class MobileDashboardWidget(
    /** 布局内唯一的短 id（前端生成）：取数结果按 id 对应，**不按下标**。 */
    @SerializedName("id") val id: String? = null,
    /** stat | line | distribution | device | service */
    @SerializedName("type") val type: String? = null,
    /** 用户输入的标题（**用户数据，不翻译**）；空 = 用类型/预置名 */
    @SerializedName("title") val title: String? = null,
    /** 预置标题的 i18n 键（仅服务端预置会填，本端按中文映射表展示） */
    @SerializedName("titleKey") val titleKey: String? = null,
    /** FULL | HALF；HALF 仅 stat / service 卡合法 */
    @SerializedName("size") val size: String? = null,
    /**
     * `LEFT` | `RIGHT`：这张半宽卡占**这一行的哪半格**。**只有 HALF 卡有**（整宽卡带了也被忽略）。
     *
     * 它取代了「连续两张 HALF 就算并排」那条隐式配对：抽走一对里的左半张，右半张**原地不动**
     * （左半格空着），不会再横着滑过去 —— 横向不补位，只有纵向让位。
     *
     * 服务端读布局时已把缺省值推好下发（见 `MobileDashboardWidgetSides`），所以正常不会见到
     * `null`；真缺省时按数组顺序推：当前行右半格空着 → `RIGHT`，否则 `LEFT`（全缺省 = 两张并排
     * 的老样子）。排布规则见 ui/home/DashboardLayout.kt。
     */
    @SerializedName("side") val side: String? = null,
    /** 按 type 的异构配置（见 [DashboardTypes] 与后端校验器） */
    @SerializedName("config") val config: Map<String, Any?> = emptyMap()
)

/** 取数请求：`widgets` 为空 = 渲染已保存的布局；非空 = 渲染草稿（编辑态预览）。 */
data class MobileRenderRequest(
    @SerializedName("widgets") val widgets: List<MobileDashboardWidget>? = null
)

/** 一次取数的结果：`widgets` 与请求同序，按各 widget 的 [id] 认领。 */
data class MobileRenderResponse(
    @SerializedName("spaceId") val spaceId: String? = null,
    @SerializedName("from") val from: Long? = null,
    @SerializedName("to") val to: Long? = null,
    @SerializedName("widgets") val widgets: List<MobileWidgetDataItem> = emptyList()
)

/** 一张卡的取数结果。`data` 与 `message` 互斥（成功带 data、失败带 message）。 */
data class MobileWidgetDataItem(
    @SerializedName("id") val id: String? = null,
    @SerializedName("success") val success: Boolean = false,
    /** 按 type 的异构数据（防御性读取见 ui/home/MobileRenderFormat.kt） */
    @SerializedName("data") val data: Map<String, Any?>? = null,
    /** 失败原因（服务端原文，原样显示） */
    @SerializedName("message") val message: String? = null
)

/**
 * 编辑器候选清单（复用的 `/matrix/v1/dashboard/web/catalog`）：设备 + 服务（全量定义，
 * 含 functions[].response 的 field/unit/valueList/bitList，供多级级联选择用）。
 */
data class MobileCatalog(
    @SerializedName("devices") val devices: List<MobileCatalogDevice> = emptyList(),
    @SerializedName("services") val services: List<ModbusService> = emptyList()
)

/** 候选设备：没有名字（显示名由前端回退链从 type 解析），只给查规格/回退所必需的输入。 */
data class MobileCatalogDevice(
    @SerializedName("did") val did: String? = null,
    /** DeviceType URN：查产品规格的键 */
    @SerializedName("type") val type: String? = null,
    /** 在线状态：恒给，false 与缺省不是一回事 */
    @SerializedName("online") val online: Boolean? = null
)