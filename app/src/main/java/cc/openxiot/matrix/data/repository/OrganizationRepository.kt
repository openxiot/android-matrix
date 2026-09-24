package cc.openxiot.matrix.data.repository

import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.api.Member
import cc.openxiot.matrix.data.api.Organization
import cc.openxiot.matrix.data.api.RetrofitClient

class OrganizationRepository {
    private val service get() = RetrofitClient.accountService

    suspend fun getMyOrganizations(): Result<List<Organization>> = runCatching {
        val response = service.getMyOrganizations()
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: emptyList()
        } else {
            response.failWith(R.string.err_org_list)
        }
    }

    suspend fun getOrganization(id: String): Result<Organization> = runCatching {
        val response = service.getOrganization(id)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data!!
        } else {
            response.failWith(R.string.err_org_info)
        }
    }

    suspend fun createOrganization(id: String, name: String): Result<Organization> = runCatching {
        val response = service.createOrganization(id, mapOf("name" to name))
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()?.data ?: Organization(id = id, name = name)
        } else {
            response.failWith(R.string.err_org_create)
        }
    }

    suspend fun updateOrganization(id: String, name: String): Result<Organization> = runCatching {
        val response = service.updateOrganization(id, mapOf("name" to name))
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()?.data ?: Organization(id = id, name = name)
        } else {
            response.failWith(R.string.err_org_update)
        }
    }

    suspend fun deleteOrganization(id: String): Result<Unit> = runCatching {
        val response = service.deleteOrganization(id)
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            response.failWith(R.string.err_org_delete)
        }
    }

    suspend fun addMember(orgId: String, member: Member): Result<Unit> = runCatching {
        val response = service.addMember(orgId, member)
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            response.failWith(R.string.err_org_member_add)
        }
    }

    suspend fun removeMember(orgId: String, memberId: String): Result<Unit> = runCatching {
        val response = service.removeMember(orgId, memberId)
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            response.failWith(R.string.err_org_member_remove)
        }
    }

    suspend fun updateMember(orgId: String, member: Member): Result<Unit> = runCatching {
        val response = service.updateMember(orgId, member)
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            response.failWith(R.string.err_org_member_update)
        }
    }
}
