package cc.openxiot.wematrix.data.repository

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
            throw Exception(response.body()?.message ?: "获取空间列表失败")
        }
    }

    suspend fun getSpace(id: String): Result<SpaceEntity> = runCatching {
        val response = service.getSpace(id)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data!!
        } else {
            throw Exception(response.body()?.message ?: "获取空间信息失败")
        }
    }

    suspend fun getSpaceTree(rootId: String): Result<SpaceEntity> = runCatching {
        val response = service.getSpaceTree(rootId)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data!!
        } else {
            throw Exception(response.body()?.message ?: "获取空间树失败")
        }
    }

    suspend fun getSpaceGraph(rootId: String): Result<SpaceGraph> = runCatching {
        val response = service.getSpaceGraph(rootId)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data!!
        } else {
            throw Exception(response.body()?.message ?: "获取空间图失败")
        }
    }

    suspend fun createSpace(space: SpaceEntity): Result<SpaceEntity> = runCatching {
        val response = service.createSpace(space)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data!!
        } else {
            throw Exception(response.body()?.message ?: "创建空间失败")
        }
    }

    suspend fun updateSpace(space: SpaceEntity): Result<SpaceEntity> = runCatching {
        val response = service.updateSpace(space)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data!!
        } else {
            throw Exception(response.body()?.message ?: "更新空间失败")
        }
    }

    suspend fun deleteSpace(spaceId: String): Result<Unit> = runCatching {
        val response = service.deleteSpace(spaceId)
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            throw Exception(response.body()?.message ?: "删除空间失败")
        }
    }

    suspend fun listAccesses(rootId: String): Result<List<ProjectMember>> = runCatching {
        val response = service.listAccesses(rootId)
        if (response.isSuccessful && response.body()?.success == true) {
            response.body()!!.data ?: emptyList()
        } else {
            throw Exception(response.body()?.message ?: "获取项目成员失败")
        }
    }

    suspend fun addAccess(rootId: String, memberId: String, role: String): Result<Unit> = runCatching {
        val response = service.addAccess(rootId, mapOf("memberId" to memberId, "role" to role))
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            throw Exception(response.body()?.message ?: "添加成员失败")
        }
    }

    suspend fun updateAccessRole(rootId: String, memberId: String, role: String): Result<Unit> = runCatching {
        val response = service.updateAccess(rootId, mapOf("memberId" to memberId, "role" to role))
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            throw Exception(response.body()?.message ?: "调整角色失败")
        }
    }

    suspend fun updateAccessRemark(rootId: String, memberId: String, remark: String): Result<Unit> = runCatching {
        val response = service.updateAccess(rootId, mapOf("memberId" to memberId, "remark" to remark))
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            throw Exception(response.body()?.message ?: "更新备注失败")
        }
    }

    suspend fun removeAccess(rootId: String, memberId: String): Result<Unit> = runCatching {
        val response = service.removeAccess(rootId, memberId)
        if (response.isSuccessful && response.body()?.success == true) {
            Unit
        } else {
            throw Exception(response.body()?.message ?: "移除成员失败")
        }
    }
}
