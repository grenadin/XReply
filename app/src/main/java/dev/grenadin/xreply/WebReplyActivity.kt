package dev.grenadin.xreply

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File

class WebReplyActivity : Activity() {
    private lateinit var replyPage: ReplyPage
    private lateinit var webView: WebView
    private lateinit var page: LinearLayout
    private lateinit var blocker: View
    private lateinit var postStatusCard: PostStatusCard
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var replyUrl: String
    private var expected = ""
    private lateinit var flow: ReplyFlow
    private var revealed = false
    private var startedAt = 0L
    private var lastLogged = ""
    private var postUrl = ""
    private val media = mutableListOf<File>()
    private var quiet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        val preloaded = ReplyPreload.take(this, id)
        replyPage = preloaded ?: ReplyPage(this, id)
        replyPage.media.addAll(media)
        webView = replyPage.webView
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
        val fromApp = intent.getBooleanExtra(EXTRA_FROM_APP, false)
        flow = ReplyFlow(media.size, fromApp, startedAt, needsFill = preloaded != null && expected.isNotBlank())
        replyPage.lean = fromApp && (expected.isNotBlank() || media.isNotEmpty())
        if (preloaded == null) {
            webView.loadUrl(replyUrl)
            log("start")
        } else {
            log("start preloaded age=${startedAt - preloaded.createdAt}")
        }
        if (expected.isBlank() && media.isEmpty()) {
            flow.manual()
            reveal(R.string.status_manual)
        } else {
            setStatus(R.string.status_sending)
            handler.postDelayed(::poll, POLL_MS)
        }
    }

    private fun poll() {
        if (isFinishing || flow.phase == ReplyFlow.Phase.DONE || flow.phase == ReplyFlow.Phase.MANUAL) return
        val script = if (flow.phase == ReplyFlow.Phase.CLICKED) Scripts.get(this, "reply_done") else Scripts.get(this, "reply_check").replace("EXPECTED", JSONObject.quote(expected)).replace("MEDIA_COUNT", media.size.toString())
        webView.evaluateJavascript(script) { raw -> onResult(raw?.trim('"').orEmpty()) }
        handler.postDelayed(::poll, POLL_MS)
    }

    private fun onResult(result: String) {
        if (flow.phase == ReplyFlow.Phase.DONE || flow.phase == ReplyFlow.Phase.MANUAL) return
        val now = System.currentTimeMillis()
        if (result != lastLogged) {
            val shown = if (result.startsWith("mismatch")) "mismatch" else result
            log("t=${now - startedAt} state=${flow.phase} revealed=$revealed result=$shown")
            lastLogged = result
        }
        for (action in flow.onResult(result, now, revealed)) when (action) {
            ReplyFlow.Action.Fill -> {
                webView.requestFocus()
                webView.evaluateJavascript(Scripts.get(this, "reply_fill").replace("TEXT", JSONObject.quote(expected))) { raw ->
                    log("t=${System.currentTimeMillis() - startedAt} fill ${raw?.trim('"')}")
                }
            }
            ReplyFlow.Action.Attach -> webView.evaluateJavascript(Scripts.get(this, "reply_attach")) { raw -> log("attach ${raw?.trim('"')}") }
            ReplyFlow.Action.Click -> webView.evaluateJavascript(Scripts.get(this, "reply_click")) { raw ->
                if (raw?.trim('"') == "clicked") {
                    log("t=${System.currentTimeMillis() - startedAt} clicked blocked=${replyPage.blocked.get()}")
                    flow.onClicked(System.currentTimeMillis())
                }
            }
            ReplyFlow.Action.Reload -> webView.loadUrl(replyUrl)
            is ReplyFlow.Action.Reveal -> {
                reveal(reasonText(action.reason))
                if (action.reason == ReplyFlow.Reason.UNKNOWN && flow.lastToast.isNotEmpty()) status.text = flow.lastToast
            }
            is ReplyFlow.Action.Sent -> {
                log("sent id=${action.id} link=${action.link}")
                handler.postDelayed({
                    if (openInX()) ReplySentCard.showLater(applicationContext, action.newPostUrl.ifEmpty { postUrl }, REPLY_SENT_CARD_DELAY_MS)
                    else Toast.makeText(this, R.string.status_sent, Toast.LENGTH_SHORT).show()
                    finish()
                }, if (revealed) FINISH_DELAY_MS else 0L)
            }
        }
    }

    private fun reasonText(reason: ReplyFlow.Reason) = when (reason) {
        ReplyFlow.Reason.NONE -> 0
        ReplyFlow.Reason.LOGIN -> R.string.status_login
        ReplyFlow.Reason.MISMATCH -> R.string.status_mismatch
        ReplyFlow.Reason.MEDIA_FAILED -> R.string.media_failed
        ReplyFlow.Reason.UNKNOWN -> R.string.status_unknown
        ReplyFlow.Reason.TIMEOUT -> R.string.status_timeout
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
        log("reveal blocked=${replyPage.blocked.get()}")
        if (replyPage.lean) {
            replyPage.lean = false
            // Reload so the visible page has its images; not after the click, and not with photos attached, which a reload would drop.
            if (replyPage.blocked.get() > 0 && media.isEmpty() && flow.phase != ReplyFlow.Phase.CLICKED) webView.loadUrl(replyUrl)
        }
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


    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (revealed && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::replyPage.isInitialized) replyPage.destroy()
        media.forEach { it.delete() }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TEXT = "text"
        const val EXTRA_USER = "user"
        const val EXTRA_FROM_APP = "from_app"
        const val EXTRA_MEDIA = "media"
        const val X_PACKAGE = "com.twitter.android"
        private const val REPLY_SENT_CARD_DELAY_MS = 900L
        private const val POLL_MS = 100L
        private const val FINISH_DELAY_MS = 1200L
        private const val TAG = "XReply"
    }
}
