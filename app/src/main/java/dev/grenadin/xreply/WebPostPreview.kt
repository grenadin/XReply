package dev.grenadin.xreply

import android.app.Activity
import org.json.JSONObject
import java.time.Instant

// Reads the post from x.com with the user's own login, for posts the public endpoints can't show (protected accounts).
object WebPostPreview {
    fun start(activity: Activity, post: Post, done: (PostPreview?) -> Unit): HiddenPage =
        HiddenPage(activity, post.url, Scripts.get(activity, "post_preview").replace("POST_ID", JSONObject.quote(post.id))) { done(it?.let(::parse)) }.start()

    private fun parse(json: String): PostPreview? = runCatching {
        val o = JSONObject(json)
        PostPreview(
            name = o.optString("name"),
            handle = o.optString("handle").removePrefix("@"),
            avatarUrl = o.optString("avatar").takeIf { it.isNotEmpty() }?.replace("_normal.", "_bigger."),
            verified = o.optBoolean("verified"),
            text = o.optString("text").trim(),
            createdAt = o.optString("time").takeIf { it.isNotEmpty() }?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
            mediaUrl = o.optString("media").takeIf { it.isNotEmpty() },
        )
    }.getOrNull()
}
