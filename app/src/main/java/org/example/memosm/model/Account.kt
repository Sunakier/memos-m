package org.example.memosm.model

data class Account(
    val hostUrl: String = "",
    val accessToken: String = "",
    val name: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val email: String? = null,
    val description: String? = null,
    val isActive: Boolean = false,
    val id: String = java.util.UUID.randomUUID().toString(),
    val lastUsed: Long = 0,
    val user: UserSnapshot? = null
)
