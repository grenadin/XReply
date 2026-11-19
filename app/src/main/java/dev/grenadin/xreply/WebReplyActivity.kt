package dev.grenadin.xreply

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.pm.ApplicationInfo
import android.util.Log
import android.graphics.Typeface
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

class WebReplyActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var replyUrl: String
    private var expected = ""
    private var state = State.LOADING
    private var readySince = 0L
    private var clickedAt = 0L
    private var seenLogin = false
    private var reloads = 0

    private enum class State { LOADING, CLICKED, DONE, MANUAL }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
        val id = intent.getStringExtra(EXTRA_ID)
        if (id == null) {
            finish()
            return
        }
        expected = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        replyUrl = PostLink.replyUrl(id, expected)
        if (expected.isBlank()) state = State.MANUAL

        val pad = (12 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { setPadding(pad, pad, pad, pad) }
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme
                    return scheme != "https" && scheme != "http"
                }
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(TextView(context).apply {
                text = getString(R.string.mode_webview_open)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(0xFF2E7D32.toInt())
                setPadding(pad, pad, pad, 0)
            })
            addView(status)
            addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        setStatus(if (state == State.MANUAL) R.string.status_manual else R.string.status_loading)
        webView.loadUrl(replyUrl)
        if (state != State.MANUAL) handler.postDelayed(::poll, POLL_MS)
    }

    private fun poll() {
        if (isFinishing || state == State.DONE || state == State.MANUAL) return
        val script = if (state == State.CLICKED) DONE_JS else CHECK_JS.replace("EXPECTED", JSONObject.quote(expected))
        webView.evaluateJavascript(script) { raw -> onResult(raw?.trim('"').orEmpty()) }
        handler.postDelayed(::poll, POLL_MS)
    }

    private var lastLogged = ""
    private var waitingSince = 0L

    private fun onResult(result: String) {
        val now = System.currentTimeMillis()
        if (result != lastLogged) {
            Log.i(TAG, "state=$state result=$result url=${webView.url}")
            lastLogged = result
        }
        if (result.startsWith("waiting") || result.startsWith("other")) {
            if (waitingSince == 0L) waitingSince = now
            if (now - waitingSince > 4000) {
                waitingSince = now
                webView.evaluateJavascript(PROBE_JS) { Log.i(TAG, "probe ${it.orEmpty()}") }
            }
        } else waitingSince = 0
        when (state) {
            State.LOADING -> when {
                result == "login" -> {
                    seenLogin = true
                    readySince = 0
                    setStatus(R.string.status_login)
                }
                result.startsWith("other") -> {
                    readySince = 0
                    if (seenLogin && reloads < MAX_RELOADS) {
                        reloads++
                        seenLogin = false
                        webView.loadUrl(replyUrl)
                    }
                }
                result.startsWith("mismatch") -> {
                    state = State.MANUAL
                    setStatus(R.string.status_mismatch)
                }
                result == "ready" -> {
                    if (readySince == 0L) readySince = now
                    setStatus(R.string.status_posting)
                    if (now - readySince >= CLICK_DELAY_MS) {
                        webView.evaluateJavascript(CLICK_JS) { raw ->
                            if (raw?.trim('"') == "clicked") {
                                state = State.CLICKED
                                clickedAt = System.currentTimeMillis()
                            }
                        }
                    }
                }
                else -> readySince = 0
            }
            State.CLICKED -> when {
                result.startsWith("toast:") -> {
                    state = State.MANUAL
                    status.text = result.removePrefix("toast:")
                }
                result.startsWith("left:") -> {
                    state = State.DONE
                    Toast.makeText(this, R.string.status_sent, Toast.LENGTH_SHORT).show()
                    handler.postDelayed(::finish, FINISH_DELAY_MS)
                }
                now - clickedAt > CONFIRM_TIMEOUT_MS -> {
                    state = State.MANUAL
                    setStatus(R.string.status_unknown)
                }
            }
            else -> Unit
        }
    }

    private fun setStatus(res: Int) {
        status.text = getString(res)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TEXT = "text"
        private const val POLL_MS = 500L
        private const val CLICK_DELAY_MS = 1000L
        private const val CONFIRM_TIMEOUT_MS = 15000L
        private const val FINISH_DELAY_MS = 1200L
        private const val MAX_RELOADS = 2
        private const val TAG = "XReply"

        private const val PROBE_JS = """(function() {
            var ids = Array.prototype.slice.call(document.querySelectorAll('[data-testid]'))
                .map(function(e) { return e.getAttribute('data-testid'); });
            var boxes = Array.prototype.slice.call(document.querySelectorAll('[contenteditable="true"], textarea'))
                .map(function(e) { return (e.getAttribute('data-testid') || e.tagName) + ':' + (e.innerText || e.value || '').length; });
            var buttons = Array.prototype.slice.call(document.querySelectorAll('[role="button"], button'))
                .map(function(e) { return (e.getAttribute('data-testid') || '-') + '=' + (e.innerText || '').trim().slice(0, 20) + (e.getAttribute('aria-disabled') === 'true' ? '(disabled)' : ''); });
            return JSON.stringify({ path: location.pathname, ids: ids.slice(0, 60), boxes: boxes, buttons: buttons.slice(0, 30) });
        })()"""

        private const val CHECK_JS = """(function(expected) {
            var path = location.pathname;
            if (path.indexOf('/login') >= 0 || path.indexOf('/flow/') >= 0) return 'login';
            if (!/^\/(intent|compose)\/(post|tweet)/.test(path)) return 'other:' + path;
            var norm = function(s) { return (s || '').replace(/\s+/g, ' ').trim(); };
            var boxes = Array.prototype.slice.call(document.querySelectorAll('[data-testid="tweetTextarea_0"]'));
            var btn = document.querySelector('[data-testid="tweetButton"]');
            if (!boxes.length || !btn) return 'waiting:' + (boxes.length ? '' : 'nobox') + (btn ? '' : 'nobtn');
            var texts = boxes.map(function(b) { return norm(b.innerText || b.textContent); });
            if (texts.indexOf(norm(expected)) < 0) {
                return texts.join('') === '' ? 'waiting:empty' : 'mismatch:' + JSON.stringify(texts) + ' vs ' + JSON.stringify(norm(expected));
            }
            if (btn.getAttribute('aria-disabled') === 'true' || btn.disabled) return 'waiting:disabled';
            return 'ready';
        })(EXPECTED)"""

        private const val CLICK_JS = """(function() {
            var btn = document.querySelector('[data-testid="tweetButton"]');
            if (!btn || btn.getAttribute('aria-disabled') === 'true') return 'no';
            btn.click();
            return 'clicked';
        })()"""

        private const val DONE_JS = """(function() {
            if (!/^\/(intent|compose)\/(post|tweet)/.test(location.pathname)) return 'left:' + location.pathname;
            var toast = document.querySelector('[data-testid="toast"]');
            if (toast && toast.innerText) return 'toast:' + toast.innerText;
            return 'waiting';
        })()"""
    }
}
