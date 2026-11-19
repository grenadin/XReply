package dev.grenadin.xreply

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ApplicationInfo
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

class WebReplyActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var page: LinearLayout
    private lateinit var blocker: View
    private lateinit var card: LinearLayout
    private lateinit var status: TextView
    private lateinit var cardStatus: TextView
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var replyUrl: String
    private var expected = ""
    private var state = State.LOADING
    private var revealed = false
    private var startedAt = 0L
    private var readySince = 0L
    private var clickedAt = 0L
    private var seenLogin = false
    private var reloads = 0
    private var lastLogged = ""

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

        val pad = dp(12)
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
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            alpha = 0f
            addView(TextView(context).apply {
                text = getString(R.string.mode_webview_open)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(0xFF2E7D32.toInt())
                setPadding(pad, pad, pad, 0)
            })
            addView(status)
            addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        blocker = View(this).apply { isClickable = true }
        cardStatus = TextView(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }
        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(16))
            background = GradientDrawable().apply {
                setColor(themeColor(android.R.attr.colorBackground))
                cornerRadius = dp(16).toFloat()
            }
            elevation = dp(8).toFloat()
            addView(ProgressBar(context))
            addView(cardStatus)
            addView(LinearLayout(context).apply {
                addView(Button(context, null, android.R.attr.borderlessButtonStyle).apply {
                    text = getString(R.string.cancel)
                    setOnClickListener { finish() }
                })
                addView(Button(context, null, android.R.attr.borderlessButtonStyle).apply {
                    text = getString(R.string.show_page)
                    setOnClickListener { reveal() }
                })
            })
        }
        setContentView(FrameLayout(this).apply {
            addView(page, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(blocker, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(card, FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.8f).toInt().coerceAtMost(dp(360)),
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        })

        startedAt = System.currentTimeMillis()
        webView.loadUrl(replyUrl)
        if (expected.isBlank()) {
            state = State.MANUAL
            reveal(R.string.status_manual)
        } else {
            setStatus(R.string.status_sending)
            handler.postDelayed(::poll, POLL_MS)
        }
    }

    private fun poll() {
        if (isFinishing || state == State.DONE || state == State.MANUAL) return
        val script = if (state == State.CLICKED) DONE_JS else CHECK_JS.replace("EXPECTED", JSONObject.quote(expected))
        webView.evaluateJavascript(script) { raw -> onResult(raw?.trim('"').orEmpty()) }
        handler.postDelayed(::poll, POLL_MS)
    }

    private fun onResult(result: String) {
        if (state == State.DONE || state == State.MANUAL) return
        val now = System.currentTimeMillis()
        if (result != lastLogged) {
            Log.i(TAG, "state=$state revealed=$revealed result=${result.substringBefore(':')}")
            lastLogged = result
        }
        when (state) {
            State.LOADING -> when {
                result == "login" -> {
                    seenLogin = true
                    readySince = 0
                    reveal(R.string.status_login)
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
                    reveal(R.string.status_mismatch)
                }
                result == "ready" -> {
                    if (readySince == 0L) readySince = now
                    if (now - readySince >= CLICK_DELAY_MS) {
                        webView.evaluateJavascript(CLICK_JS) { raw ->
                            if (raw?.trim('"') == "clicked" && state == State.LOADING) {
                                state = State.CLICKED
                                clickedAt = System.currentTimeMillis()
                            }
                        }
                    }
                }
                else -> readySince = 0
            }
            State.CLICKED -> when {
                result.startsWith("left:") -> {
                    state = State.DONE
                    Toast.makeText(this, R.string.status_sent, Toast.LENGTH_SHORT).show()
                    handler.postDelayed(::finish, if (revealed) FINISH_DELAY_MS else 0L)
                }
                result.startsWith("toast:") -> {
                    state = State.MANUAL
                    reveal()
                    status.text = result.removePrefix("toast:")
                }
                now - clickedAt > CONFIRM_TIMEOUT_MS -> {
                    state = State.MANUAL
                    reveal(R.string.status_unknown)
                }
            }
            else -> Unit
        }
        if (state == State.LOADING && !revealed && now - startedAt > HIDDEN_TIMEOUT_MS) {
            reveal(R.string.status_timeout)
        }
    }

    private fun reveal(res: Int = 0) {
        if (res != 0) setStatus(res)
        if (revealed) return
        revealed = true
        Log.i(TAG, "reveal")
        page.alpha = 1f
        page.setBackgroundColor(themeColor(android.R.attr.colorBackground))
        blocker.visibility = View.GONE
        card.visibility = View.GONE
    }

    private fun setStatus(res: Int) {
        status.text = getString(res)
        cardStatus.text = getString(res)
    }

    private fun themeColor(attr: Int): Int {
        val value = TypedValue()
        theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (revealed && webView.canGoBack()) webView.goBack() else super.onBackPressed()
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
        private const val HIDDEN_TIMEOUT_MS = 20000L
        private const val FINISH_DELAY_MS = 1200L
        private const val MAX_RELOADS = 2
        private const val TAG = "XReply"

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
