package dev.grenadin.xreply

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

// WebView set up the same way everywhere XReply opens x.com with the user's login.
object XWebView {
    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, lean: () -> Boolean = { false }, blocked: AtomicInteger? = null): WebView {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme
                    return scheme != "https" && scheme != "http"
                }

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (!lean() || !isHeavy(request)) return null
                    blocked?.incrementAndGet()
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }

    // Photos, videos, emoji images and web fonts: not needed while the reply page is hidden.
    private fun isHeavy(request: WebResourceRequest): Boolean {
        val url = request.url
        val host = url.host ?: return false
        val path = url.path.orEmpty()
        return host == "pbs.twimg.com" || host == "video.twimg.com" ||
            (host.endsWith("twimg.com") && (path.contains("/emoji/") || FONT.containsMatchIn(path)))
    }

    private val FONT = Regex("\\.(woff2?|ttf|otf)$")
}
