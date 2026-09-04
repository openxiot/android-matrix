package cc.openxiot.wematrix.data.api

import retrofit2.Response
import retrofit2.http.*

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

    @POST("matrix/v1/device/properties/{spaceId}")
    suspend fun setDeviceProperties(
        @Path("spaceId") spaceId: String,
        @Body body: Map<String, Any>
    ): Response<ApiResponse<List<Map<String, Any?>>>>

    @POST("matrix/v1/device/actions/{spaceId}")
    suspend fun invokeDeviceAction(
        @Path("spaceId") spaceId: String,
        @Body body: Map<String, Any>
    ): Response<ApiResponse<List<Map<String, Any?>>>>
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
}

interface DtuService {
    /** 根据 IMEI 查询设备 DID，返回 { success, data = did } */
    @GET("v1/did/by/imei")
    suspend fun getDidByImei(
        @Query("orgId") orgId: String,
        @Query("imei") imei: String
    ): Response<ApiResponse<String>>
}
