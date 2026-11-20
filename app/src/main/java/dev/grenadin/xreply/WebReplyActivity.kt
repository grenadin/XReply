package dev.grenadin.xreply

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File

class WebReplyActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var page: LinearLayout
    private lateinit var blocker: View
    private lateinit var postStatusCard: PostStatusCard
    private lateinit var status: TextView
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
    private var postUrl = ""
    private val media = mutableListOf<File>()
    private var attachedAt = 0L
    private var lastToast = ""
    private var leftAt = 0L
    private var quiet = false

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

        postUrl = Post(id, intent.getStringExtra(EXTRA_USER)).url
        intent.getStringArrayListExtra(EXTRA_MEDIA)?.map(::File)?.filter { it.exists() }?.take(MediaFiles.MAX_IMAGES)?.let(media::addAll)

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
        if (media.isNotEmpty()) webView.addJavascriptInterface(MediaBridge(media), "XReplyMedia")
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
        postStatusCard = PostStatusCard(this, themeColor(android.R.attr.colorBackground), onCancel = ::finish, onShowPage = { reveal() })
        setContentView(FrameLayout(this).apply {
            addView(page, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(blocker, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(postStatusCard, FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.8f).toInt().coerceAtMost(dp(360)),
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        })

        quiet = !getSharedPreferences(ReplyActivity.PREFS, MODE_PRIVATE).getBoolean(ReplyActivity.PREF_SHOW_POST_STATUS, true)
        if (quiet) {
            postStatusCard.visibility = View.GONE
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            window.attributes = window.attributes.apply { alpha = 0f }
        }

        startedAt = System.currentTimeMillis()
        webView.loadUrl(replyUrl)
        log("start")
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
        val script = if (state == State.CLICKED) DONE_JS else CHECK_JS.replace("EXPECTED", JSONObject.quote(expected)).replace("MEDIA_COUNT", media.size.toString())
        webView.evaluateJavascript(script) { raw -> onResult(raw?.trim('"').orEmpty()) }
        handler.postDelayed(::poll, POLL_MS)
    }

    private fun onResult(result: String) {
        if (state == State.DONE || state == State.MANUAL) return
        val now = System.currentTimeMillis()
        if (result != lastLogged) {
            val shown = if (result.startsWith("mismatch")) "mismatch" else result
            log("t=${now - startedAt} state=$state revealed=$revealed result=$shown")
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
                result == "needmedia" -> {
                    readySince = 0
                    if (attachedAt == 0L) {
                        attachedAt = now
                        webView.evaluateJavascript(ATTACH_JS) { raw -> log("attach ${raw?.trim('"')}") }
                    } else if (now - attachedAt > MEDIA_TIMEOUT_MS) {
                        state = State.MANUAL
                        reveal(R.string.media_failed)
                    }
                }
                result == "ready" && !intent.getBooleanExtra(EXTRA_FROM_APP, false) -> {
                    state = State.MANUAL
                    reveal()
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
                    val parts = result.removePrefix("left:").split("|")
                    val id = parts.getOrElse(1) { "" }
                    val link = parts.getOrElse(2) { "" }
                    if (leftAt == 0L) leftAt = now
                    if (id.isEmpty() && link.isEmpty() && now - leftAt < NEW_POST_WAIT_MS) return
                    state = State.DONE
                    val newPost = when {
                        link.contains("/status/") -> link
                        id.isNotEmpty() -> "https://x.com/i/status/$id"
                        else -> ""
                    }
                    log("sent id=$id link=$link")
                    handler.postDelayed({
                        if (openInX()) ReplySentCard.showLater(applicationContext, newPost.ifEmpty { postUrl }, REPLY_SENT_CARD_DELAY_MS)
                        else Toast.makeText(this, R.string.status_sent, Toast.LENGTH_SHORT).show()
                        finish()
                    }, if (revealed) FINISH_DELAY_MS else 0L)
                }
                result.startsWith("toast:") -> lastToast = result.removePrefix("toast:")
                now - clickedAt > (if (media.isEmpty()) CONFIRM_TIMEOUT_MS else CONFIRM_TIMEOUT_MEDIA_MS) -> {
                    state = State.MANUAL
                    reveal(R.string.status_unknown)
                    if (lastToast.isNotEmpty()) status.text = lastToast
                }
            }
            else -> Unit
        }
        val hiddenTimeout = if (media.isEmpty()) HIDDEN_TIMEOUT_MS else HIDDEN_TIMEOUT_MEDIA_MS
        if (state == State.LOADING && !revealed && now - startedAt > hiddenTimeout) {
            reveal(R.string.status_timeout)
        }
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        runCatching { File(cacheDir, "xreply.log").appendText("${System.currentTimeMillis()} $message\n") }
    }

    private fun openInX(): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(postUrl)).setPackage(X_PACKAGE))
        true
    } catch (e: ActivityNotFoundException) {
        log("X app not available")
        false
    }

    private fun reveal(res: Int = 0) {
        if (res != 0) setStatus(res)
        if (revealed) return
        revealed = true
        log("reveal")
        if (quiet) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            window.attributes = window.attributes.apply { alpha = 1f }
        }
        page.alpha = 1f
        page.setBackgroundColor(themeColor(android.R.attr.colorBackground))
        blocker.visibility = View.GONE
        postStatusCard.visibility = View.GONE
    }

    private fun setStatus(res: Int) {
        status.text = getString(res)
        postStatusCard.setStatus(getString(res))
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
        media.forEach { it.delete() }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TEXT = "text"
        const val EXTRA_USER = "user"
        const val EXTRA_FROM_APP = "from_app"
        const val EXTRA_MEDIA = "media"
        private const val MEDIA_TIMEOUT_MS = 10000L
        private const val HIDDEN_TIMEOUT_MEDIA_MS = 60000L
        const val X_PACKAGE = "com.twitter.android"
        private const val NEW_POST_WAIT_MS = 1500L
        private const val REPLY_SENT_CARD_DELAY_MS = 900L
        private const val POLL_MS = 250L
        private const val CLICK_DELAY_MS = 300L
        private const val CONFIRM_TIMEOUT_MS = 15000L
        private const val CONFIRM_TIMEOUT_MEDIA_MS = 45000L
        private const val HIDDEN_TIMEOUT_MS = 20000L
        private const val FINISH_DELAY_MS = 1200L
        private const val MAX_RELOADS = 2
        private const val TAG = "XReply"

        private const val CHECK_JS = """(function(expected, mediaCount) {
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
            if (mediaCount > 0) {
                var scope = document.querySelector('[role="dialog"]') || document;
                var att = scope.querySelector('[data-testid="attachments"]');
                var imgs = att ? att.querySelectorAll('img').length : 0;
                var busy = att ? !!att.querySelector('[role="progressbar"]') : false;
                if (imgs === 0 && !busy) return 'needmedia';
                if (imgs < mediaCount || busy) return 'waiting:upload';
            }
            if (btn.getAttribute('aria-disabled') === 'true' || btn.disabled) return 'waiting:disabled';
            return 'ready';
        })(EXPECTED, MEDIA_COUNT)"""

        private const val ATTACH_JS = """(function() {
            var inputs = Array.prototype.slice.call(document.querySelectorAll('input[data-testid="fileInput"]'));
            var input = inputs.filter(function(i) { return i.closest('[role="dialog"]'); })[0] || inputs[0];
            if (!input || !window.XReplyMedia) return 'noinput';
            var dt = new DataTransfer();
            for (var i = 0; i < XReplyMedia.count(); i++) {
                var raw = atob(XReplyMedia.data(i));
                var bytes = new Uint8Array(raw.length);
                for (var j = 0; j < raw.length; j++) bytes[j] = raw.charCodeAt(j);
                dt.items.add(new File([bytes], XReplyMedia.name(i), { type: XReplyMedia.type(i) }));
            }
            input.files = dt.files;
            input.dispatchEvent(new Event('change', { bubbles: true }));
            return 'attached:' + dt.files.length;
        })()"""

        private const val CLICK_JS ="""(function() {
            if (!window.__xreplyHooked) {
                window.__xreplyHooked = true;
                var grab = function(body) {
                    try {
                        var d = typeof body === 'string' ? JSON.parse(body) : body;
                        var r = d.data.create_tweet.tweet_results.result;
                        var id = r.rest_id || (r.tweet && r.tweet.rest_id);
                        if (id) window.__xreplyId = id;
                    } catch (e) {}
                };
                var origFetch = window.fetch;
                if (origFetch) window.fetch = function(input) {
                    var p = origFetch.apply(this, arguments);
                    try {
                        var u = typeof input === 'string' ? input : (input && input.url) || '';
                        if (/CreateTweet/.test(u)) p.then(function(r) { r.clone().text().then(grab); }).catch(function() {});
                    } catch (e) {}
                    return p;
                };
                var origOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(m, u) {
                    if (/CreateTweet/.test(String(u))) this.addEventListener('load', function() {
                        try { grab(this.responseType === '' || this.responseType === 'text' ? this.responseText : this.response); } catch (e) {}
                    });
                    return origOpen.apply(this, arguments);
                };
            }
            var btn = document.querySelector('[data-testid="tweetButton"]');
            if (!btn || btn.getAttribute('aria-disabled') === 'true') return 'no';
            btn.click();
            return 'clicked';
        })()"""

        private const val DONE_JS = """(function() {
            if (!/^\/(intent|compose)\/(post|tweet)/.test(location.pathname)) {
                var a = document.querySelector('[data-testid="toast"] a[href*="/status/"]');
                return 'left:' + location.pathname + '|' + (window.__xreplyId || '') + '|' + (a ? a.href : '');
            }
            var toast = document.querySelector('[data-testid="toast"]');
            if (toast && toast.innerText) return 'toast:' + toast.innerText;
            return 'waiting';
        })()"""
    }
}

private class MediaBridge(private val files: List<File>) {
    @JavascriptInterface fun count() = files.size
    @JavascriptInterface fun name(i: Int): String = files[i].name
    @JavascriptInterface fun type(i: Int): String = MediaFiles.mimeOf(files[i])
    @JavascriptInterface fun data(i: Int): String = Base64.encodeToString(files[i].readBytes(), Base64.NO_WRAP)
}
