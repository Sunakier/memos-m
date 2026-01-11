package org.example.memosm.model

import com.google.gson.annotations.SerializedName

data class Account(
    @SerializedName("hostUrl") val hostUrl: String,
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("isActive") val isActive: Boolean = false
)
