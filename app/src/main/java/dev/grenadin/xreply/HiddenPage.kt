package dev.grenadin.xreply

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray

// Loads an x.com page with the user's own login in an invisible WebView and polls readJs until it returns JSON.
// readJs returns a JSON object string when done, 'fail' to give up, anything else to keep waiting.
class HiddenPage(
    private val activity: Activity,
    private val url: String,
    private val readJs: String,
    private val done: (String?) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var startedAt = 0L

    @SuppressLint("SetJavaScriptEnabled")
    fun start(): HiddenPage {
        val view = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            alpha = 0f
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = request.url.scheme != "https"
            }
        }
        webView = view
        (activity.window.decorView as ViewGroup).addView(view, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        startedAt = System.currentTimeMillis()
        view.loadUrl(url)
        handler.postDelayed(::poll, POLL_MS)
        return this
    }

    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        webView = null
    }

    private fun poll() {
        val view = webView ?: return
        view.evaluateJavascript(readJs) { raw ->
            if (webView == null) return@evaluateJavascript
            val result = runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("")
            when {
                result.startsWith("{") -> finish(result)
                result == "fail" || System.currentTimeMillis() - startedAt > TIMEOUT_MS -> finish(null)
                else -> handler.postDelayed(::poll, POLL_MS)
            }
        }
    }

    private fun finish(result: String?) {
        cancel()
        done(result)
    }

    companion object {
        private const val POLL_MS = 400L
        private const val TIMEOUT_MS = 12000L

        const val LOGIN_CHECK_JS = "if (/^\\/(i\\/flow\\/login|login)/.test(location.pathname)) return 'fail';"
    }
}
