package org.example.memosm.api

import org.example.memosm.model.Shortcut
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** API additions introduced after the v0.30.0 release. */
interface MemosApiV0300 : MemosApiV0280 {
    @GET("api/v1/{user}/views")
    suspend fun listMemoViewsV0300(
        @Path("user", encoded = true) user: String
    ): ListMemoViewsResponseV0300

    @POST("api/v1/{user}/views")
    suspend fun createMemoViewV0300(
        @Path("user", encoded = true) user: String,
        @Body memoView: Shortcut,
        @Query("validateOnly") validateOnly: Boolean? = null
    ): Shortcut

    @DELETE("api/v1/{user}/views/{view}")
    suspend fun deleteMemoViewV0300(
        @Path("user", encoded = true) user: String,
        @Path("view", encoded = true) view: String
    )

    @PATCH("api/v1/{user}/views/{view}")
    suspend fun updateMemoViewV0300(
        @Path("user", encoded = true) user: String,
        @Path("view", encoded = true) view: String,
        @Body memoView: Shortcut,
        @Query("updateMask") updateMask: String? = null
    ): Shortcut
}

data class ListMemoViewsResponseV0300(
    val memoViews: List<Shortcut>? = null
)
