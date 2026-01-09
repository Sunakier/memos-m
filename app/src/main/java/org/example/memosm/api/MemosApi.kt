package org.example.memosm.api

import org.example.memosm.model.ListMemosResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface MemosApi {

    @GET("api/v1/memos")
    suspend fun listMemos(
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("state") state: String? = null,
        @Query("orderBy") orderBy: String? = null,
        @Query("filter") filter: String? = null,
        @Query("showDeleted") showDeleted: Boolean? = null,
    ): ListMemosResponse
}