package org.example.memosm.api

import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

open class SessionCookieJar(
    private val onCookiesUpdated: ((Map<String, String>) -> Unit)? = null
) : CookieJar {

    private val cookieStore = mutableMapOf<String, MutableMap<String, Cookie>>()

    // Helper to load initial cookies
    fun loadCookies(cookies: Map<String, String>) {
        // We don't have domain info in the map, so we might need a better strategy if we support multiple domains strictly.
        // For now, we'll store them generally or rely on saving them when they come in.
        // Actually, okhttp needs cookies per domain.
        // If we only persist 'name=value' pairs, we lose domain info.
        // But usually we are talking to one host per account.
        // Let's assume the cookies apply to the account's host.
        // BUT, loadForRequest provides the URL.
        
        // Strategy: We won't pre-fill store from the map easily without domain.
        // Instead, the Map<String, String> is a flattened "name: value" representation we give back to the app 
        // to save. When loading, we might need a way to restore them properly.
        
        // Actually, if we just want to send the "token" cookie, we might just manually add it 
        // OR we can reconstruct Cookies if we assume the host.
        // Since we don't have the host here easily in 'loadCookies' without passing it.
    }
    
    // Better signature for loading
    fun restoreCookies(url: HttpUrl, savedCookies: Map<String, String>) {
        val host = url.host
        val cookieMap = cookieStore.getOrPut(host) { mutableMapOf() }
        
        Log.d("SessionCookieJar", "Restoring ${savedCookies.size} cookies for $host")
        
        savedCookies.forEach { (name, value) ->
             val cookie = Cookie.Builder()
                 .name(name)
                 .value(value)
                 .domain(host)
                 .path("/") // Assume root path
                 .build()
             cookieMap[name] = cookie
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        Log.d("SessionCookieJar", "saveFromResponse called for $host with ${cookies.size} cookies")
        val cookieMap = cookieStore.getOrPut(host) { mutableMapOf() }
        
        var changed = false
        cookies.forEach { cookie ->
            if (cookieMap[cookie.name] != cookie) {
                cookieMap[cookie.name] = cookie
                changed = true
            }
        }
        
        if (changed) {
            val flattened = cookieMap.values.associate { it.name to it.value }
            onCookiesUpdated?.invoke(flattened)
            Log.d("SessionCookieJar", "Cookies updated for $host: ${cookieMap.keys}")
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = cookieStore[host]?.values?.toList() ?: emptyList()
        if (cookies.isNotEmpty()) {
            Log.d("SessionCookieJar", "Loading ${cookies.size} cookies for $host: ${cookies.map { "${it.name}=...${it.value.takeLast(4)}" }}")
        } else {
            Log.d("SessionCookieJar", "No cookies found for $host")
        }
        return cookies
    }
}
