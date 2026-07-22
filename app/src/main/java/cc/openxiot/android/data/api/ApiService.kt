package cc.openxiot.android.data.api

import retrofit2.Response
import retrofit2.http.*

interface AccountService {
    @GET("developer/platform/all")
    suspend fun getPlatforms(): Response<ApiResponse<List<PlatformInfo>>>

    @GET("developer/platform/github")
    suspend fun getGithubPlatform(): Response<ApiResponse<PlatformInfo>>

    @GET("organization/many")
    suspend fun getMyOrganizations(): Response<ApiResponse<List<Organization>>>

    @GET("organization/all")
    suspend fun getAllOrganizations(): Response<ApiResponse<List<Organization>>>

    @GET("organization/one/{id}")
    suspend fun getOrganization(@Path("id") organizationId: String): Response<ApiResponse<Organization>>

    @POST("organization/one/{id}")
    suspend fun createOrganization(
        @Path("id") organizationId: String,
        @Body body: Map<String, String>
    ): Response<ApiResponse<Organization>>

    @PUT("organization/one/{id}")
    suspend fun updateOrganization(
        @Path("id") organizationId: String,
        @Body body: Map<String, String>
    ): Response<ApiResponse<Organization>>

    @DELETE("organization/one/{id}")
    suspend fun deleteOrganization(@Path("id") organizationId: String): Response<ApiResponse<Unit>>

    @POST("organization/member/{orgId}")
    suspend fun addMember(
        @Path("orgId") orgId: String,
        @Body member: Member
    ): Response<ApiResponse<Unit>>

    @DELETE("organization/member/{orgId}")
    suspend fun removeMember(
        @Path("orgId") orgId: String,
        @Query("memberId") memberId: String
    ): Response<ApiResponse<Unit>>

    @PUT("organization/member/{orgId}")
    suspend fun updateMember(
        @Path("orgId") orgId: String,
        @Body member: Member
    ): Response<ApiResponse<Unit>>
}

interface SiteService {
    @GET("v1/space/all")
    suspend fun getAllSpaces(): Response<ApiResponse<List<SpaceEntity>>>

    @GET("v1/space/one/{id}")
    suspend fun getSpace(@Path("id") spaceId: String): Response<ApiResponse<SpaceEntity>>

    @GET("v1/space/tree/{rootId}")
    suspend fun getSpaceTree(@Path("rootId") rootId: String): Response<ApiResponse<SpaceEntity>>

    @GET("v1/space/graph/{rootId}")
    suspend fun getSpaceGraph(@Path("rootId") rootId: String): Response<ApiResponse<SpaceGraph>>

    @POST("v1/space/one")
    suspend fun createSpace(@Body space: SpaceEntity): Response<ApiResponse<SpaceEntity>>

    @PUT("v1/space/one")
    suspend fun updateSpace(@Body space: SpaceEntity): Response<ApiResponse<SpaceEntity>>

    @DELETE("v1/space/one/{spaceId}")
    suspend fun deleteSpace(@Path("spaceId") spaceId: String): Response<ApiResponse<Unit>>

    @GET("v1/device/many/{spaceId}")
    suspend fun getDevices(@Path("spaceId") spaceId: String): Response<ApiResponse<List<DeviceEntity>>>

    @POST("v1/device/many/{spaceId}")
    suspend fun addDevices(
        @Path("spaceId") spaceId: String,
        @Body devices: List<DeviceRegistration>
    ): Response<ApiResponse<Unit>>

    @POST("v1/device/one/{spaceId}")
    suspend fun addDeviceByQr(
        @Path("spaceId") spaceId: String,
        @Body body: Map<String, String>
    ): Response<ApiResponse<Unit>>

    @PUT("v1/device/many/space")
    suspend fun updateDeviceSpace(@Body body: MoveDeviceRequest): Response<ApiResponse<Unit>>
}

interface ProductService {
    @GET("v1/product/basic/public")
    suspend fun getProducts(): Response<ApiResponse<List<ProductEntity>>>

    @GET("v1/product/basic/one/org-model")
    suspend fun getProductByOrgModel(
        @Query("organizationId") orgId: String,
        @Query("model") model: String
    ): Response<ApiResponse<ProductEntity>>
}
