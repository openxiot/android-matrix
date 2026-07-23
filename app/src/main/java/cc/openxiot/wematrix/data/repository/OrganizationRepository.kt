package cc.openxiot.wematrix.data.repository

import cc.openxiot.wematrix.data.api.Member
import cc.openxiot.wematrix.data.api.Organization
import cc.openxiot.wematrix.data.api.RetrofitClient

class OrganizationRepository {
    private val service get() = RetrofitClient.accountService

    suspend fun getMyOrganizations(): Result<List<Organization>> = runCatching {
        val response = service.getMyOrganizations()
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: emptyList()
        } else {
            throw Exception(response.body()?.message ?: "获取组织列表失败")
        }
    }

    suspend fun getOrganization(id: String): Result<Organization> = runCatching {
        val response = service.getOrganization(id)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data!!
        } else {
            throw Exception(response.body()?.message ?: "获取组织信息失败")
        }
    }

    suspend fun createOrganization(id: String, name: String): Result<Organization> = runCatching {
        val response = service.createOrganization(id, mapOf("name" to name))
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()?.data ?: Organization(id = id, name = name)
        } else {
            throw Exception(response.body()?.message ?: "创建组织失败")
        }
    }

    suspend fun updateOrganization(id: String, name: String): Result<Organization> = runCatching {
        val response = service.updateOrganization(id, mapOf("name" to name))
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()?.data ?: Organization(id = id, name = name)
        } else {
            throw Exception(response.body()?.message ?: "更新组织失败")
        }
    }

    suspend fun deleteOrganization(id: String): Result<Unit> = runCatching {
        val response = service.deleteOrganization(id)
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            throw Exception(response.body()?.message ?: "删除组织失败")
        }
    }

    suspend fun addMember(orgId: String, member: Member): Result<Unit> = runCatching {
        val response = service.addMember(orgId, member)
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            throw Exception(response.body()?.message ?: "添加成员失败")
        }
    }

    suspend fun removeMember(orgId: String, memberId: String): Result<Unit> = runCatching {
        val response = service.removeMember(orgId, memberId)
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            throw Exception(response.body()?.message ?: "移除成员失败")
        }
    }
}
