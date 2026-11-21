package dev.grenadin.xreply

import android.app.Activity
import android.content.Context
import org.json.JSONObject

// Avatar of the account logged in to XReply's WebView, read from x.com and cached per X user id.
object SelfAvatar {
    private const val PREFS = "self_avatar"
    private const val KEY_USER = "user"
    private const val KEY_URL = "url"
    private const val KEY_AT = "at"
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

    fun cached(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val user = XLogin.userId() ?: return null
        return prefs.getString(KEY_URL, null)?.takeIf { prefs.getString(KEY_USER, null) == user }
    }

    fun isFresh(context: Context): Boolean {
        val at = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_AT, 0)
        return cached(context) != null && System.currentTimeMillis() - at < MAX_AGE_MS
    }

    fun fetch(activity: Activity, done: (String?) -> Unit): HiddenPage =
        HiddenPage(activity, "https://x.com/home", READ_JS) { json ->
            val url = json?.let { runCatching { JSONObject(it).optString("avatar") }.getOrNull() }
                ?.takeIf { it.startsWith("https://") }
                ?.replace("_normal.", "_bigger.")
                ?.replace("_x96.", "_bigger.")
            val user = XLogin.userId()
            if (url != null && user != null) {
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_USER, user).putString(KEY_URL, url).putLong(KEY_AT, System.currentTimeMillis()).apply()
            }
            done(url)
        }.start()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private const val READ_JS = """(function() {
        ${HiddenPage.LOGIN_CHECK_JS}
        var url = '';
        try {
            var s = window.__INITIAL_STATE__;
            var u = s.entities.users.entities[s.session.user_id];
            if (u && u.profile_image_url_https) url = u.profile_image_url_https;
        } catch (e) {}
        if (!url) {
            var img = document.querySelector('[data-testid="DashButton_ProfileIcon_Link"] img, [data-testid="SideNav_AccountSwitcher_Button"] img');
            if (img) url = img.src;
        }
        return url ? JSON.stringify({ avatar: url }) : 'wait';
    })()"""
}
