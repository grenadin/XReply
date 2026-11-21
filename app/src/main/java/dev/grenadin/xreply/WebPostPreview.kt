package dev.grenadin.xreply

import android.app.Activity
import org.json.JSONObject
import java.time.Instant

// Reads the post from x.com with the user's own login, for posts the public endpoints can't show (protected accounts).
object WebPostPreview {
    fun start(activity: Activity, post: Post, done: (PostPreview?) -> Unit): HiddenPage =
        HiddenPage(activity, post.url, READ_JS.replace("POST_ID", JSONObject.quote(post.id))) { done(it?.let(::parse)) }.start()

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

    private const val READ_JS = """(function(id) {
        ${HiddenPage.LOGIN_CHECK_JS}
        var articles = document.querySelectorAll('article[data-testid="tweet"]');
        var art = null;
        for (var i = 0; i < articles.length; i++) {
            if (articles[i].querySelector('a[href$="/status/' + id + '"]')) { art = articles[i]; break; }
        }
        if (!art) art = document.querySelector('article[data-testid="tweet"][tabindex="-1"]');
        if (!art) return 'wait';
        var names = art.querySelector('[data-testid="User-Name"]');
        if (!names) return 'wait';
        var lines = names.innerText.split('\n').map(function(s) { return s.trim(); }).filter(Boolean);
        var handle = '';
        for (var j = 0; j < lines.length; j++) if (lines[j].charAt(0) === '@') { handle = lines[j]; break; }
        var text = art.querySelector('[data-testid="tweetText"]');
        var avatar = art.querySelector('[data-testid="Tweet-User-Avatar"] img');
        var time = art.querySelector('time');
        var photo = art.querySelector('[data-testid="tweetPhoto"] img');
        return JSON.stringify({
            name: lines[0] || '',
            handle: handle,
            verified: !!art.querySelector('[data-testid="icon-verified"]'),
            text: text ? text.innerText : '',
            avatar: avatar ? avatar.src : '',
            time: time ? time.getAttribute('datetime') : '',
            media: photo ? photo.src : ''
        });
    })(POST_ID)"""
}
