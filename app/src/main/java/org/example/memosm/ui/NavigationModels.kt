package org.example.memosm.ui

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MemoKey(val id: String, val fromSearch: Boolean = false) : Parcelable

@Parcelize
sealed class ProfileDetailKey : Parcelable {
    @Parcelize
    object Archived : ProfileDetailKey()
}
