package org.example.memosm.ui.components.item.media

import java.util.concurrent.ConcurrentHashMap

object MediaCache {
    private val aspectRatios = ConcurrentHashMap<String, Float>()

    fun getAspectRatio(key: String?): Float? {
        if (key.isNullOrBlank()) return null
        return aspectRatios[key]
    }

    fun setAspectRatio(key: String?, ratio: Float) {
        if (key.isNullOrBlank()) return
        aspectRatios[key] = ratio
    }
}
