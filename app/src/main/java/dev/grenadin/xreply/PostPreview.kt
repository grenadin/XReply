package dev.grenadin.xreply

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

data class PostPreview(
    val name: String,
    val handle: String,
    val avatarUrl: String?,
    val verified: Boolean,
    val text: String,
    val createdAt: Long?,
    val mediaUrl: String?,
)

object PostPreviewSource {
    private const val TIMEOUT_MS = 8000

    fun fetch(post: Post): PostPreview? =
        runCatching { fromSyndication(get("https://cdn.syndication.twimg.com/tweet-result?id=${post.id}&lang=en&token=x")) }.getOrNull()
            ?: runCatching { fromOEmbed(get("https://publish.twitter.com/oembed?omit_script=1&url=" + URLEncoder.encode(post.url, "UTF-8"))) }.getOrNull()

    fun fromSyndication(json: String): PostPreview? {
        val o = JSONObject(json)
        val user = o.optJSONObject("user") ?: return null
        val mediaUrls = mutableListOf<String>()
        o.optJSONObject("entities")?.optJSONArray("media")?.let { media ->
            for (i in 0 until media.length()) media.optJSONObject(i)?.optString("url")?.takeIf { it.isNotEmpty() }?.let(mediaUrls::add)
        }
        val mediaUrl = o.optJSONArray("mediaDetails")?.optJSONObject(0)?.optString("media_url_https")?.takeIf { it.isNotEmpty() }
        return PostPreview(
            name = user.optString("name"),
            handle = user.optString("screen_name"),
            avatarUrl = user.optString("profile_image_url_https").takeIf { it.isNotEmpty() }?.replace("_normal.", "_bigger."),
            verified = user.optBoolean("is_blue_verified") || user.optBoolean("verified"),
            text = cleanText(o.optString("text"), mediaUrls),
            createdAt = runCatching { Instant.parse(o.getString("created_at")).toEpochMilli() }.getOrNull(),
            mediaUrl = mediaUrl,
        )
    }

    fun fromOEmbed(json: String): PostPreview? {
        val o = JSONObject(json)
        val handle = o.optString("author_url").substringAfterLast('/')
        if (handle.isEmpty()) return null
        val paragraph = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL).find(o.optString("html"))?.groupValues?.get(1).orEmpty()
        val text = paragraph
            .replace(Regex("<a[^>]*>pic\\.twitter\\.com/[^<]*</a>"), "")
            .replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
        return PostPreview(o.optString("author_name"), handle, null, false, unescape(text).trim(), null, null)
    }

    fun cleanText(text: String, mediaUrls: List<String>): String {
        var result = text
        for (url in mediaUrls) result = result.replace(url, "")
        return unescape(result).trim()
    }

    private fun unescape(text: String) = text
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&mdash;", "—").replace("&amp;", "&")

    fun relativeTime(createdAt: Long, now: Long, nowLabel: String, locale: Locale): String {
        val seconds = (now - createdAt) / 1000
        return when {
            seconds < 5 -> nowLabel
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m"
            seconds < 86400 -> "${seconds / 3600}h"
            else -> SimpleDateFormat("d MMM", locale).format(Date(createdAt))
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) XReply")
        }

    private fun get(url: String): String {
        val connection = open(url)
        try {
            if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    fun decodeFile(file: java.io.File, maxPx: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    fun loadBitmap(url: String, maxPx: Int): Bitmap? = runCatching {
        val connection = open(url)
        val bytes = try {
            if (connection.responseCode != 200) return null
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
