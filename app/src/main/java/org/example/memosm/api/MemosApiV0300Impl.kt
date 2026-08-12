package org.example.memosm.api

import org.example.memosm.model.Shortcut
import org.example.memosm.model.ShortcutResponse

class MemosApiV0300Impl(
    private val apiV0300: MemosApiV0300
) : MemosApi by MemosApiV0280Impl(apiV0300) {
    override suspend fun getShortcuts(user: String): ShortcutResponse {
        return ShortcutResponse(shortcuts = apiV0300.listMemoViewsV0300(user).memoViews)
    }

    override suspend fun createShortcut(
        user: String,
        shortcut: Shortcut,
        validateOnly: Boolean?
    ): Shortcut {
        return apiV0300.createMemoViewV0300(user, shortcut, validateOnly)
    }

    override suspend fun deleteShortcut(user: String, shortcut: String) {
        return apiV0300.deleteMemoViewV0300(user, shortcut)
    }

    override suspend fun updateShortcut(
        user: String,
        shortcut: String,
        shortcutData: Shortcut,
        updateMask: String?
    ): Shortcut {
        return apiV0300.updateMemoViewV0300(user, shortcut, shortcutData, updateMask)
    }
}
