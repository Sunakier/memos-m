package org.example.memosm.model

data class UserResponse(
    val user: User?
)

data class User(
    val name: String? = null,
    val role: String? = null,
    val username: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val description: String? = null,
    val state: String? = null,
    val createTime: String? = null,
    val updateTime: String? = null
)

data class UserStats(
    val name: String,
    val memoDisplayTimestamps: List<String>,
    val memoTypeStats: MemoTypeStats,
    val tagCount: Map<String, Int>,
    val pinnedMemos: List<String>,
    val totalMemoCount: Int
)

data class MemoTypeStats(
    val linkCount: Int, val codeCount: Int, val todoCount: Int, val undoCount: Int
)

data class ShortcutResponse(
    val shortcuts: List<Shortcut>
)

data class Shortcut(
    val name: String, val title: String, val filter: String
)

data class InstanceProfile(
    val owner: String, val version: String, val mode: String, val instanceUrl: String
)
