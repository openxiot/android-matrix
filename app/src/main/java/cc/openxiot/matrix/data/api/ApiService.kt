package cc.openxiot.matrix.data.api

import retrofit2.Response
import retrofit2.http.*

/**
 * 各服务的 Retrofit 接口。
 *
 * **`@Body` 的泛型实参一律写 `@JvmSuppressWildcards`**（如 `Map<String, @JvmSuppressWildcards Any?>`）：
 * Kotlin 生成 Java 签名时，对非 final 的实参（`Any` / `Any?`）会补一个通配符 ——
 * `Map<String, Any?>` 编出来是 `Map<String, ? extends Object>`，而 Retrofit **拒绝**参数类型里
 * 出现通配符与类型变量。麻烦的是它**懒校验**：`create()` 一声不吭，等真的点了那个按钮才抛
 * `Parameter type must not include a type variable or wildcard`（告警页的「处理」就这么炸过一次）。
 * 实参是 final 类（`String` / `Boolean`）时 Kotlin 本来就不补通配符，故本文件里 `Map<String, String>`
 * 那几处不带也安全；`Any` / `Any?` 这种非 final 的则一律要带。
 *
 * 这类错编译期与 lint 都看不见，由 [ApiServiceSignatureTest] 在构建期逐个方法解析一遍钉住。
 */
interface AccountService {
    // 移动端直接换 token 的登录接口(区别于浏览器用的 callback 302 跳转),返回 JSON
    @GET("user/oauth2/v1/login/mobile/{platformId}")
    suspend fun oauthLogin(
        @Path("platformId") platformId: String,
        @Query("code") code: String
    ): Response<ApiResponse<OAuthToken>>

    @GET("user/platform/all")
    suspend fun getPlatforms(): Response<ApiResponse<List<PlatformInfo>>>

    @GET("user/platform/github")
    suspend fun getGithubPlatform(): Response<ApiResponse<PlatformInfo>>

    @GET("user/organization/many")
    suspend fun getMyOrganizations(): Response<ApiResponse<List<Organization>>>

    @GET("user/organization/all")
    suspend fun getAllOrganizations(): Response<ApiResponse<List<Organization>>>

    @GET("user/organization/one/{id}")
    suspend fun getOrganization(@Path("id") organizationId: String): Response<ApiResponse<Organization>>

    @POST("user/organization/one/{id}")
    suspend fun createOrganization(
        @Path("id") organizationId: String,
        @Body body: Map<String, String>
    ): Response<ApiResponse<Organization>>

    @PUT("user/organization/one/{id}")
    suspend fun updateOrganization(
        @Path("id") organizationId: String,
        @Body body: Map<String, String>
    ): Response<ApiResponse<Organization>>

    @DELETE("user/organization/one/{id}")
    suspend fun deleteOrganization(@Path("id") organizationId: String): Response<ApiResponse<Unit>>

    @POST("user/organization/member/{orgId}")
    suspend fun addMember(
        @Path("orgId") orgId: String,
        @Body member: Member
    ): Response<ApiResponse<Unit>>

    @DELETE("user/organization/member/{orgId}")
    suspend fun removeMember(
        @Path("orgId") orgId: String,
        @Query("memberId") memberId: String
    ): Response<ApiResponse<Unit>>

    @PUT("user/organization/member/{orgId}")
    suspend fun updateMember(
        @Path("orgId") orgId: String,
        @Body member: Member
    ): Response<ApiResponse<Unit>>

    // ---- 用户设置 ----

    @GET("user/settings")
    suspend fun getSettings(): Response<ApiResponse<UserSettings>>

    @PUT("user/settings")
    suspend fun updateSettings(@Body body: Map<String, Boolean>): Response<ApiResponse<UserSettings>>
}

interface MatrixService {
    @GET("matrix/v1/space/all")
    suspend fun getAllSpaces(): Response<ApiResponse<List<SpaceEntity>>>

    @GET("matrix/v1/space/one/{id}")
    suspend fun getSpace(@Path("id") spaceId: String): Response<ApiResponse<SpaceEntity>>

    @GET("matrix/v1/space/tree/{rootId}")
    suspend fun getSpaceTree(@Path("rootId") rootId: String): Response<ApiResponse<SpaceEntity>>

    @GET("matrix/v1/space/graph/{rootId}")
    suspend fun getSpaceGraph(@Path("rootId") rootId: String): Response<ApiResponse<SpaceGraph>>

    @POST("matrix/v1/space/one")
    suspend fun createSpace(@Body space: SpaceEntity): Response<ApiResponse<SpaceEntity>>

    @PUT("matrix/v1/space/one")
    suspend fun updateSpace(@Body space: SpaceEntity): Response<ApiResponse<SpaceEntity>>

    @DELETE("matrix/v1/space/one/{spaceId}")
    suspend fun deleteSpace(@Path("spaceId") spaceId: String): Response<ApiResponse<Unit>>

    // ---- 项目成员（根空间 access 条目） ----

    @GET("matrix/v1/space/{rootId}/access")
    suspend fun listAccesses(@Path("rootId") rootId: String): Response<ApiResponse<List<ProjectMember>>>

    @POST("matrix/v1/space/{rootId}/access")
    suspend fun addAccess(
        @Path("rootId") rootId: String,
        @Body body: Map<String, String>
    ): Response<ApiResponse<Unit>>

    @PUT("matrix/v1/space/{rootId}/access")
    suspend fun updateAccess(
        @Path("rootId") rootId: String,
        @Body body: Map<String, String>
    ): Response<ApiResponse<Unit>>

    @DELETE("matrix/v1/space/{rootId}/access")
    suspend fun removeAccess(
        @Path("rootId") rootId: String,
        @Query("memberId") memberId: String
    ): Response<ApiResponse<Unit>>

    @GET("matrix/v1/device/many/{spaceId}")
    suspend fun getDevices(@Path("spaceId") spaceId: String): Response<ApiResponse<List<DeviceEntity>>>

    @POST("matrix/v1/device/many/{spaceId}")
    suspend fun addDevices(
        @Path("spaceId") spaceId: String,
        @Body devices: List<DeviceRegistration>
    ): Response<ApiResponse<Unit>>

    @POST("matrix/v1/device/one/{spaceId}")
    suspend fun addDeviceByQr(
        @Path("spaceId") spaceId: String,
        @Body body: Map<String, String>
    ): Response<ApiResponse<Unit>>

    @PUT("matrix/v1/device/many/space")
    suspend fun updateDeviceSpace(@Body body: MoveDeviceRequest): Response<ApiResponse<Unit>>

    @GET("matrix/v1/device/properties/{spaceId}")
    suspend fun getDeviceProperties(
        @Path("spaceId") spaceId: String,
        @Query("pid") pid: List<String>
    ): Response<ApiResponse<List<Map<String, Any?>>>>

    /**
     * 写属性。body 是**数组**（[{pid,value}]），与 web 端
     * `PropertyOperationCodec.Set.QUERY.encodeArray` 同口型（返回值也是数组的 {pid,status}）。
     */
    @POST("matrix/v1/device/properties/{spaceId}")
    suspend fun setDeviceProperties(
        @Path("spaceId") spaceId: String,
        @Body body: List<@JvmSuppressWildcards Map<String, @JvmSuppressWildcards Any?>>
    ): Response<ApiResponse<List<Map<String, Any?>>>>

    /**
     * 执行方法。body 是**数组**（[{aid,in:[{piid,values}]}]），与 web 端
     * `ActionOperationCodec.Query.encodeArray` 同口型（返回值也是数组的 {aid,status,out}）。
     */
    @POST("matrix/v1/device/actions/{spaceId}")
    suspend fun invokeDeviceAction(
        @Path("spaceId") spaceId: String,
        @Body body: List<@JvmSuppressWildcards Map<String, @JvmSuppressWildcards Any?>>
    ): Response<ApiResponse<List<Map<String, Any?>>>>

    // ---- 设备点表（只读） ----
    // 组织经拦截器附加的 X-Org-Id 携带，方法不传 orgId（口径同 web 的 modbus.service.ts）。
    // 写接口（POST/PUT/DELETE 与 lifecycle 流转）本端不使用，故不声明。

    /** 当前账号可见的全部设备点表：本组织私有 + 各组织公开 */
    @GET("matrix/v1/modbus/config/visible")
    suspend fun getVisibleModbusConfigs(): Response<ApiResponse<List<ModbusConfig>>>

    /** 全部公开设备点表（仅要求登录，不校验组织）：未选组织时的回退 */
    @GET("matrix/v1/modbus/config/public")
    suspend fun getPublicModbusConfigs(): Response<ApiResponse<List<ModbusConfig>>>

    /** 查询单条设备点表 */
    @GET("matrix/v1/modbus/config/one/{id}")
    suspend fun getModbusConfig(@Path("id") id: String): Response<ApiResponse<ModbusConfig>>

    // ---- Modbus 服务 ----
    // 路径里的 spaceId 是**鉴权作用域**而不是过滤条件：后端只拿它校验「当前用户是该空间成员」，
    // 真正的查询按 id / did 走（见 ModbusServiceResource 的 requireSpaceMember）。
    // 故这里统一传**当前项目根空间**，与 web 的 modbus.service.ts 同口径。
    // 增删改需要空间管理员（POST/PUT/DELETE），本端不做，故不声明。

    /** 查询单条服务（完整定义：含方法与请求帧） */
    @GET("matrix/v1/modbus/service/one/{spaceId}/{id}")
    suspend fun getModbusService(
        @Path("spaceId") spaceId: String,
        @Path("id") id: String
    ): Response<ApiResponse<ModbusService>>

    /** 列出挂在指定设备下的全部服务。只按 did 过滤，不受设备落点空间影响 */
    @GET("matrix/v1/modbus/service/parent/{spaceId}/{did}")
    suspend fun getModbusServicesByDevice(
        @Path("spaceId") spaceId: String,
        @Path("did") did: String
    ): Response<ApiResponse<List<ModbusService>>>

    /**
     * 调用一个方法：把该方法的请求帧发给依赖设备，返回应答解出的「字段名 → 值」。
     * 写方法（response 为空）返回空对象。
     */
    @POST("matrix/v1/modbus/service/invoke/{spaceId}")
    suspend fun invokeModbusService(
        @Path("spaceId") spaceId: String,
        @Body body: InvokeModbusServiceRequest
    ): Response<ApiResponse<Map<String, Any?>>>

    // ---- 数据看板统计 ----
    // 与查询服务同口径（空间成员），spaceId 在 Path 上、统一传当前项目**根空间**。
    // **范围含子空间**：设备、服务、告警、故障都取该空间整棵子树。

    /**
     * 数据看板一屏的聚合数字。
     *
     * `from` 必填（后端拒无起点的查询：那是全表扫）；`to` 传 null = 到现在，
     * 响应里的 `to` 是**实际生效**的那个值，页面的曲线右端以它为准。
     * 窗口上限 31 天，超了后端报错（不会静默截断）。
     */
    @GET("matrix/v1/statistics/overview/{spaceId}")
    suspend fun getStatisticsOverview(
        @Path("spaceId") spaceId: String,
        @Query("from") from: Long,
        @Query("to") to: Long?
    ): Response<ApiResponse<OverviewStatistics>>

    // ---- Modbus 采集历史（只读） ----
    // 服务端按各方法的 interval 自动调用依赖设备、把读到的值落库，下面三个接口分别取
    // 「当前值 / 序列 / 失败清单」。spaceId 同样是**鉴权作用域**（见上）。
    //
    // **每个可空查询参数都是「不传 = 不限」**：Retrofit 对 null 参数默认就不拼进 URL，
    // 正好是要的行为 —— 故**绝不能**给它们默认值（给了 false / 空串就永远查不了对应的子集）。
    // 服务端对这些空值的口径：`serviceId` 不传 = 整个空间（含子空间），响应里每条 item
    // 自带 serviceId 认领归属。

    /** 每个方法最后一次**成功**采到的字段值 + 那一刻，以及最近一次失败 */
    @GET("matrix/v1/modbus/history/current/{spaceId}/{serviceId}")
    suspend fun getHistoryCurrent(
        @Path("spaceId") spaceId: String,
        @Path("serviceId") serviceId: String
    ): Response<ApiResponse<ModbusHistoryCurrent>>

    /**
     * 一个方法的某一个字段在 [from, to] 内的序列。
     *
     * `to` 缺省 = 现在；`maxPoints` 缺省 500、后端夹到 [1, 2000]，原始样本超过它就返回降采样桶。
     * 窗口内原始样本超过 2 万条时后端直接报错，要求收窄 from/to（不会静默截断）。
     */
    @GET("matrix/v1/modbus/history/range/{spaceId}")
    suspend fun getHistoryRange(
        @Path("spaceId") spaceId: String,
        @Query("serviceId") serviceId: String,
        @Query("functionIndex") functionIndex: Int,
        @Query("field") field: String,
        @Query("from") from: Long,
        @Query("to") to: Long?,
        @Query("maxPoints") maxPoints: Int?
    ): Response<ApiResponse<ModbusHistoryRange>>

    /**
     * 某服务（或整个空间）在 [from, to] 内的采集失败清单 + 汇总，items 按时间倒序。
     *
     * `serviceId` / `type` / `functionIndex` 传 null = 不限；`limit` 缺省 200、后端夹到 [1, 1000]。
     * `type` 是**失败类型**筛选（后端枚举名，见 ModbusHistoryFormat 的映射）—— 现页面按类型
     * 展示汇总而不按它筛，故调用方暂时一律传 null，参数留着是为了让契约完整。
     */
    @GET("matrix/v1/modbus/history/failures/{spaceId}")
    suspend fun getHistoryFailures(
        @Path("spaceId") spaceId: String,
        @Query("from") from: Long,
        @Query("to") to: Long?,
        @Query("serviceId") serviceId: String?,
        @Query("functionIndex") functionIndex: Int?,
        @Query("type") type: String?,
        @Query("limit") limit: Int?
    ): Response<ApiResponse<ModbusHistoryFailures>>

    // ---- 阈值告警 ----

    /**
     * 某服务（或整个空间）在 [from, to] 内的阈值告警清单 + 汇总，items 按 `at` 倒序。
     *
     * `serviceId` 不传 = **整个空间（含子空间）**：后端把空间下所有服务的告警合成一条时间倒序的
     * 清单，`limit` 与 `truncated` 也按整份清单算。「这个项目现在哪儿在告警」就是这一条，
     * 不必按服务扇出 N 个请求。
     *
     * `level` / `field` / `serviceId` 传 null = 不限；`open` / `handled` 是**三态**
     * （true 只看未恢复/未处理、false 只看已恢复/已处理、null 不限）—— 它们的 null 与 false
     * **必须区分开**，用 false 表达「不传」就永远查不了「只看已恢复」的那些。
     *
     * `from` 必填（后端拒无起点的查询），`to` 传 null = 到现在。
     */
    @GET("matrix/v1/modbus/alarm/many/{spaceId}")
    suspend fun getModbusAlarms(
        @Path("spaceId") spaceId: String,
        @Query("from") from: Long,
        @Query("to") to: Long?,
        @Query("serviceId") serviceId: String?,
        @Query("functionIndex") functionIndex: Int?,
        @Query("field") field: String?,
        @Query("level") level: String?,
        @Query("open") openState: Boolean?,
        @Query("handled") handled: Boolean?,
        @Query("limit") limit: Int?
    ): Response<ApiResponse<ModbusAlarmList>>

    /**
     * 处理一条告警：处理人取当前登录账号，返回**更新后的那一条**，页面据此就地替换该行、
     * 不整页刷新。
     *
     * 重复点击算成功（后端把「已经处理过了」当成功返回）：页面不必自己做「点过了就禁用」，
     * 但仍应就地更新，否则用户会以为没生效。该告警不属于传入空间的子树时后端拒绝（拿别人的 id 点不了）。
     *
     * 本端**唯一的新增写操作**，且是 web 已有的幂等接口；后端这个方法的签名里没有 body 参数
     * （web 的 `{}` 是 Angular `post()` 必须给个 body 的产物），本端照 web 传一个空对象。
     */
    @POST("matrix/v1/modbus/alarm/handle/{spaceId}/{id}")
    suspend fun handleModbusAlarm(
        @Path("spaceId") spaceId: String,
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<ApiResponse<ModbusAlarm>>

    // ---- 移动端看板（可自定义首页） ----
    // 只读（layout/preset/render/catalog）成员可调；PUT 保存需空间管理员。
    // spaceId 是**鉴权作用域**，统一传当前项目根空间。@Body 都是具体类，无通配符问题。

    /** 读布局；从未保存时后端返回预置（version=0），不会 404。 */
    @GET("matrix/v1/dashboard/mobile/layout/{spaceId}")
    suspend fun getMobileLayout(
        @Path("spaceId") spaceId: String
    ): Response<ApiResponse<MobileDashboardLayout>>

    /** 保存布局（乐观锁 CAS：带上的 version 不匹配则被服务端拒绝）。 */
    @PUT("matrix/v1/dashboard/mobile/layout/{spaceId}")
    suspend fun saveMobileLayout(
        @Path("spaceId") spaceId: String,
        @Body layout: MobileDashboardLayout
    ): Response<ApiResponse<MobileDashboardLayout>>

    /** 只读预置（不进库，无副作用）。 */
    @GET("matrix/v1/dashboard/mobile/layout/{spaceId}/preset")
    suspend fun getMobileLayoutPreset(
        @Path("spaceId") spaceId: String
    ): Response<ApiResponse<MobileDashboardLayout>>

    /** 取数：body 的 widgets 为空 = 渲染已保存布局；非空 = 渲染草稿。 */
    @POST("matrix/v1/dashboard/mobile/render/{spaceId}")
    suspend fun renderMobileDashboard(
        @Path("spaceId") spaceId: String,
        @Body request: MobileRenderRequest
    ): Response<ApiResponse<MobileRenderResponse>>

    /** 编辑器候选清单（复用 web 的 catalog：成员可读，返回全量设备 + 服务定义）。 */
    @GET("matrix/v1/dashboard/web/catalog/{spaceId}")
    suspend fun getDashboardCatalog(
        @Path("spaceId") spaceId: String
    ): Response<ApiResponse<MobileCatalog>>
}

interface ProductService {
    @GET("v1/product/basic/public")
    suspend fun getProducts(): Response<ApiResponse<List<ProductEntity>>>

    @GET("v1/product/basic/visible/{organization}")
    suspend fun getVisibleProducts(
        @Path("organization") orgId: String
    ): Response<ApiResponse<List<ProductEntity>>>

    @GET("v1/product/instance/one/{type}")
    suspend fun getProductInstance(@Path("type") type: String): Response<ApiResponse<Map<String, Any?>>>

    @GET("v1/product/basic/one/org-model")
    suspend fun getProductByOrgModel(
        @Query("organizationId") orgId: String,
        @Query("model") model: String
    ): Response<ApiResponse<ProductEntity>>

    @GET("v1/product/basic/one")
    suspend fun getProductDetail(@Query("productId") productId: String): Response<ApiResponse<ProductEntity>>

    /**
     * 按设备类型 + category 取产品控制页列表（设备详情页要内嵌的控制页，category=移动端的 `mobile`）。
     * product 主机在 RetrofitClient 里被当作公开主机、不加鉴权头 —— 与 web 端口径一致。
     */
    @GET("v1/product/controller/many/by-device-type")
    suspend fun getControllersByDeviceType(
        @Query("deviceType") deviceType: String,
        @Query("category") category: String
    ): Response<ApiResponse<List<ProductController>>>
}

interface DtuService {
    /** 根据 IMEI 查询设备 DID，返回 { success, data = did } */
    @GET("v1/did/by/imei")
    suspend fun getDidByImei(
        @Query("orgId") orgId: String,
        @Query("imei") imei: String
    ): Response<ApiResponse<String>>
}
