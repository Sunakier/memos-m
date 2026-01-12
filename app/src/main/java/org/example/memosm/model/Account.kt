package org.example.memosm.model

data class Account(
    val hostUrl: String,
    val accessToken: String,
    val name: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val isActive: Boolean = false
)
