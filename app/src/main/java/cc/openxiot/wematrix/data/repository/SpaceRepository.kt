package cc.openxiot.wematrix.data.repository

import cc.openxiot.wematrix.R
import cc.openxiot.wematrix.data.api.ProjectMember
import cc.openxiot.wematrix.data.api.SpaceEntity
import cc.openxiot.wematrix.data.api.SpaceGraph
import cc.openxiot.wematrix.data.api.RetrofitClient

class SpaceRepository {
    private val service get() = RetrofitClient.matrixService

    suspend fun getAllSpaces(): Result<List<SpaceEntity>> = runCatching {
        val response = service.getAllSpaces()
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: emptyList()
        } else {
            response.failWith(R.string.err_space_list)
        }
    }

    suspend fun getSpace(id: String): Result<SpaceEntity> = runCatching {
        val response = service.getSpace(id)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data!!
        } else {
            response.failWith(R.string.err_space_info)
        }
    }

    suspend fun getSpaceTree(rootId: String): Result<SpaceEntity> = runCatching {
        val response = service.getSpaceTree(rootId)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data!!
        } else {
            response.failWith(R.string.err_space_tree)
        }
    }

    suspend fun getSpaceGraph(rootId: String): Result<SpaceGraph> = runCatching {
        val response = service.getSpaceGraph(rootId)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data!!
        } else {
            response.failWith(R.string.err_space_graph)
        }
    }

    suspend fun createSpace(space: SpaceEntity): Result<SpaceEntity> = runCatching {
        val response = service.createSpace(space)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data!!
        } else {
            response.failWith(R.string.err_space_create)
        }
    }

    suspend fun updateSpace(space: SpaceEntity): Result<SpaceEntity> = runCatching {
        val response = service.updateSpace(space)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data!!
        } else {
            response.failWith(R.string.err_space_update)
        }
    }

    suspend fun deleteSpace(spaceId: String): Result<Unit> = runCatching {
        val response = service.deleteSpace(spaceId)
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            response.failWith(R.string.err_space_delete)
        }
    }

    suspend fun listAccesses(rootId: String): Result<List<ProjectMember>> = runCatching {
        val response = service.listAccesses(rootId)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: emptyList()
        } else {
            response.failWith(R.string.err_space_member_list)
        }
    }

    suspend fun addAccess(rootId: String, memberId: String, role: String): Result<Unit> = runCatching {
        val response = service.addAccess(rootId, mapOf("memberId" to memberId, "role" to role))
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            response.failWith(R.string.err_space_member_add)
        }
    }

    suspend fun updateAccessRole(rootId: String, memberId: String, role: String): Result<Unit> = runCatching {
        val response = service.updateAccess(rootId, mapOf("memberId" to memberId, "role" to role))
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            response.failWith(R.string.err_space_member_role)
        }
    }

    suspend fun updateAccessRemark(rootId: String, memberId: String, remark: String): Result<Unit> = runCatching {
        val response = service.updateAccess(rootId, mapOf("memberId" to memberId, "remark" to remark))
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            response.failWith(R.string.err_space_member_note)
        }
    }

    suspend fun removeAccess(rootId: String, memberId: String): Result<Unit> = runCatching {
        val response = service.removeAccess(rootId, memberId)
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            response.failWith(R.string.err_space_member_remove)
        }
    }
}
