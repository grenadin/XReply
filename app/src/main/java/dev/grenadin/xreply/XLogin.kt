package dev.grenadin.xreply

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

object XLogin {
    fun isLoggedIn() = CookieManager.getInstance().getCookie("https://x.com")?.contains("auth_token=") == true

    fun clear(context: Context) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        WebView(context).apply {
            clearCache(true)
            destroy()
        }
    }
}
