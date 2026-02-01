package org.example.memosm.api

import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.java.net.cookiejar.JavaNetCookieJar
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI

class MemosCookieJar(
    private val onCookiesUpdated: ((Map<String, String>) -> Unit)? = null
) : CookieJar {

    private val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }
    private val delegate = JavaNetCookieJar(cookieManager)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val cookiesToSave = cookies.filter { it.name != "memos_refresh" || it.value.isNotEmpty() }
        if (cookiesToSave.isNotEmpty()) {
            Log.d("MemosCookieJar", "Saving ${cookiesToSave.size} cookies for $url: $cookiesToSave")
            for (cookie in cookiesToSave) {
                Log.d("MemosCookieJar", "Cookie: ${cookie.name}=${cookie.value.take(10)}...")
            }
        }

        delegate.saveFromResponse(url, cookiesToSave)
        
        // Convert to simple map for persistence (lossy, but matching existing DataStore)
        // Ideally we should persist full objects, but for now specific session cookies rely on name=value
        val simpleCookies = mutableMapOf<String, String>()
        cookieManager.cookieStore.cookies.forEach { httpCookie ->
            simpleCookies[httpCookie.name] = httpCookie.value
        }
        
        if (simpleCookies.isNotEmpty()) {
            Log.d("MemosCookieJar", "Persisting ${simpleCookies.size} cookies to callback")
        }
        
        onCookiesUpdated?.invoke(simpleCookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookies = delegate.loadForRequest(url)
        if (cookies.isNotEmpty()) {
            Log.d("MemosCookieJar", "Loaded ${cookies.size} cookies for $url: ${cookies.map { it.name }}")
        }
        return cookies
    }

    fun restore(url: HttpUrl, cookies: Map<String, String>) {
        if (cookies.isNotEmpty()) {
            Log.d("MemosCookieJar", "Restoring ${cookies.size} cookies for $url")
        }
        // We have to guess the domain/path if relying on generic Map
        // Standard Memos cookies are usually for root path
        val domain = url.host
        val uri = url.toUri() // java.net.URI

        cookies.forEach { (name, value) ->
            Log.d("MemosCookieJar", "Restoring cookie $name=${value.take(10)}... for $url")
            val cookie = HttpCookie(name, value)
            cookie.domain = domain
            cookie.path = "/"
            cookie.version = 0
            
            cookieManager.cookieStore.add(uri, cookie)
        }
    }
    
    fun get(name: String): String? {
        return cookieManager.cookieStore.cookies.find { it.name == name }?.value
    }
}
