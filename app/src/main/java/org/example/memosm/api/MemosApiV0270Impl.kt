package org.example.memosm.api

import org.example.memosm.model.CurrentSessionResponse
import org.example.memosm.model.SignInRequest
import org.example.memosm.model.SignInRequestV0260
import org.example.memosm.model.SignInResponse

open class MemosApiV0270Impl(
    private val apiV0270: MemosApiV0270
) : MemosApiV0260Impl(apiV0270) {
    override val constants = super.constants.copy(
        memoCreatorFilterStyle = MemoCreatorFilterStyle.RESOURCE_NAME,
        memoOrderByPinnedDesc = "pinned desc, display_time desc",
        memoOrderByNewest = "display_time desc",
        memoOrderByOldest = "display_time asc"
    )

    // We inherit all the updated endpoints from v0.26.0
    // Currently, v0.27 shares the same signIn and getCurrentUser structures
}
