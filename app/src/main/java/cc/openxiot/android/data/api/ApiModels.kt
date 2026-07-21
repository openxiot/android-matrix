package cc.openxiot.android.data.api

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: T? = null,
    @SerializedName("message") val message: String? = null
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
    @SerializedName("_id") val id: String? = null,
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
    @SerializedName("developerId") val developerId: String,
    @SerializedName("name") val name: String,
    @SerializedName("role") val role: String,
    @SerializedName("email") val email: String? = null
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
    @SerializedName("createTime") val createTime: String? = null,
    @SerializedName("updateTime") val updateTime: String? = null
)

data class DeviceEntity(
    @SerializedName("did") val did: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("online") val online: Boolean? = null,
    @SerializedName("protocol") val protocol: String? = null,
    @SerializedName("lastOnline") val lastOnline: String? = null,
    @SerializedName("lastOffline") val lastOffline: String? = null
)

data class SpaceGraph(
    @SerializedName("spaces") val spaces: List<SpaceEntity>? = null,
    @SerializedName("devices") val devices: List<DeviceEntity>? = null
)

data class DeviceRegistration(
    @SerializedName("did") val did: String,
    @SerializedName("type") val type: String
)

data class ProductEntity(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("version") val version: String? = null
)
