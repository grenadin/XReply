package dev.grenadin.xreply

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

object XLogin {
    fun isLoggedIn() = CookieManager.getInstance().getCookie("https://x.com")?.contains("auth_token=") == true

    // twid cookie = "u%3D<user id>"
    fun userId(): String? = CookieManager.getInstance().getCookie("https://x.com")
        ?.split(';')?.map { it.trim() }
        ?.firstOrNull { it.startsWith("twid=") }
        ?.removePrefix("twid=")?.replace("\"", "")?.replace("%3D", "=")?.removePrefix("u=")
        ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

    fun clear(context: Context) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        SelfAvatar.clear(context)
        WebView(context).apply {
            clearCache(true)
            destroy()
        }
    }
}
