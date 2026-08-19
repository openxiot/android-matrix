package cc.openxiot.wematrix.data.api

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: T? = null,
    @SerializedName("message") val message: String? = null
)

data class OAuthToken(
    @SerializedName("token") val token: String?,
    @SerializedName("name") val name: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("platform") val platform: String? = null
)

data class PlatformInfo(
    @SerializedName("platformId") val platformId: String,
    @SerializedName("platformName") val platformName: String,
    @SerializedName("clientId") val clientId: String,
    @SerializedName("callbackUrl") val callbackUrl: String,
    @SerializedName("authorizeUrl") val authorizeUrl: String,
    @SerializedName("icon") val icon: String?,
    @SerializedName("available") val available: Boolean
)

data class Organization(
    @SerializedName("code") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("creator") val creator: Creator? = null,
    @SerializedName("members") val members: List<Member>? = null
)

data class Creator(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null
)

data class Member(
    @SerializedName("developerId") val developerId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("email") val email: String? = null
)

/**
 * 项目（根空间）成员视图，对应后端 matrix 服务 /access 返回的 SpaceAccessView
 * （accesses 中 type = user 的条目）。与组织成员的 [Member]（developerId 字段）不同，
 * 项目成员用 userId 标识账号。
 */
data class ProjectMember(
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("remark") val remark: String? = null
)

data class SpaceEntity(
    @SerializedName("id") val id: String? = null,
    @SerializedName("tenantId") val tenantId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("typeAlias") val typeAlias: String? = null,
    @SerializedName("parentId") val parentId: String? = null,
    @SerializedName("rootId") val rootId: String? = null,
    @SerializedName("level") val level: Int? = null,
    @SerializedName("ancestors") val ancestors: List<String>? = null,
    @SerializedName("sortOrder") val sortOrder: Int? = null,
    @SerializedName("children") val children: List<SpaceEntity>? = null,
    @SerializedName("devices") val devices: List<DeviceEntity>? = null,
    @SerializedName("accesses") val accesses: List<SpaceAccess>? = null,
    @SerializedName("createTime") val createTime: String? = null,
    @SerializedName("updateTime") val updateTime: String? = null
)

/**
 * 空间访问条目（SpaceEntity.accesses）：type = organization / user。
 * 项目成员页用 organization 条目做管理员"组织兜底"判定。
 */
data class SpaceAccess(
    @SerializedName("id") val id: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("remark") val remark: String? = null
)

data class DeviceEntity(
    @SerializedName("did") val did: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("online") val online: Boolean? = null,
    @SerializedName("protocol") val protocol: String? = null,
    @SerializedName("lastOnline") val lastOnline: String? = null,
    @SerializedName("lastOffline") val lastOffline: String? = null,
    @SerializedName("space") val space: DeviceSpaceRef? = null
)

data class DeviceSpaceRef(
    @SerializedName("spaceId") val spaceId: String? = null,
    @SerializedName("rootId") val rootId: String? = null
)

data class SpaceGraph(
    @SerializedName("spaces") val spaces: List<SpaceEntity>? = null,
    @SerializedName("devices") val devices: List<DeviceEntity>? = null
)

data class DeviceRegistration(
    @SerializedName("did") val did: String,
    @SerializedName("type") val type: String
)

data class MoveDeviceRequest(
    @SerializedName("spaceId") val spaceId: String,
    @SerializedName("rootSpaceId") val rootSpaceId: String,
    @SerializedName("dids") val dids: List<String>
)

data class LocalizedName(
    @SerializedName("zh-CN") val zhCN: String? = null
)

data class ProductEntity(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: LocalizedName? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("protocol") val protocol: String? = null,
    @SerializedName("lifecycle") val lifecycle: String? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("organization") val organization: String? = null,
    @SerializedName("template") val template: String? = null
) {
    val displayName: String get() = name?.zhCN ?: model ?: id ?: "未知产品"
}
