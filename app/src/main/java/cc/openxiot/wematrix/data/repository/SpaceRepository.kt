package cc.openxiot.wematrix.data.repository

import cc.openxiot.wematrix.data.api.SpaceEntity
import cc.openxiot.wematrix.data.api.SpaceGraph
import cc.openxiot.wematrix.data.api.RetrofitClient

class SpaceRepository {
    private val service get() = RetrofitClient.siteService

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
}
