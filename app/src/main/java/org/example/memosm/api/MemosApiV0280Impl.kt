package org.example.memosm.api

class MemosApiV0280Impl(
    apiV0280: MemosApiV0280
) : MemosApiV0270Impl(apiV0280) {
    override val constants = super.constants.copy(
        memoOrderByPinnedDesc = "pinned desc, create_time desc",
        memoOrderByNewest = "create_time desc",
        memoOrderByOldest = "create_time asc"
    )
}
