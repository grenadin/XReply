package dev.grenadin.xreply

import android.annotation.SuppressLint
import android.app.Activity
import android.content.MutableContextWrapper
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

// The WebView that posts one reply. It can be created early by ReplyPreload and moved to WebReplyActivity on send.
class ReplyPage(activity: Activity, val postId: String) {
    private val context = MutableContextWrapper(activity)
    @Volatile var lean = true
    val blocked = AtomicInteger()
    val media = CopyOnWriteArrayList<File>()
    // The bridge hands out the photos only while XReply runs its own attach script.
    @Volatile var mediaOpen = false
    val createdAt = System.currentTimeMillis()
    val webView = XWebView.create(context, lean = { lean }, blocked = blocked).apply {
        addJavascriptInterface(MediaBridge(media) { mediaOpen }, "XReplyMedia")
    }

    fun attach(activity: Activity) {
        context.baseContext = activity
    }

    fun detach() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        context.baseContext = context.applicationContext
    }

    fun destroy() {
        detach()
        webView.destroy()
    }
}

private class MediaBridge(private val files: List<File>, private val open: () -> Boolean) {
    @JavascriptInterface fun count() = if (open()) files.size else 0
    @JavascriptInterface fun name(i: Int): String = file(i).name
    @JavascriptInterface fun type(i: Int): String = MediaFiles.mimeOf(file(i))
    @JavascriptInterface fun data(i: Int): String = Base64.encodeToString(file(i).readBytes(), Base64.NO_WRAP)

    private fun file(i: Int): File {
        check(open())
        return files[i]
    }
}

// Opens the reply page while the user is still typing, so sending only has to fill in the text and click.
object ReplyPreload {
    private const val MAX_AGE_MS = 10 * 60 * 1000L
    private var page: ReplyPage? = null
    private var owner: Activity? = null

    @SuppressLint("ClickableViewAccessibility")
    fun start(activity: Activity, postId: String) {
        if (page?.postId == postId && owner === activity) return
        release()
        val p = ReplyPage(activity, postId)
        p.webView.apply {
            alpha = 0f
            isFocusable = false
            isFocusableInTouchMode = false
            setOnTouchListener { _, _ -> true }
        }
        (activity.window.decorView as ViewGroup).addView(p.webView, 0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        p.webView.loadUrl(PostLink.replyUrl(postId, ""))
        page = p
        owner = activity
    }

    fun handOff() {
        page?.detach()
        owner = null
    }

    fun releaseFor(activity: Activity) {
        if (owner === activity) release()
    }

    fun take(activity: Activity, postId: String): ReplyPage? {
        val p = page ?: return null
        page = null
        owner = null
        if (p.postId != postId || System.currentTimeMillis() - p.createdAt > MAX_AGE_MS) {
            p.destroy()
            return null
        }
        p.detach()
        p.attach(activity)
        p.webView.apply {
            alpha = 1f
            isFocusable = true
            isFocusableInTouchMode = true
            setOnTouchListener(null)
        }
        return p
    }

    private fun release() {
        page?.destroy()
        page = null
        owner = null
    }
}
