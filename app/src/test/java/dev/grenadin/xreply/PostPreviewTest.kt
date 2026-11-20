package dev.grenadin.xreply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PostPreviewTest {
    @Test
    fun syndicationDropsMediaLinkAndUnescapes() {
        val json = """{"text":"Too realistic &amp; fun 💀 #GTA6  https://t.co/abc","created_at":"2026-11-19T03:35:29.000Z",
            "entities":{"media":[{"url":"https://t.co/abc"}]},
            "mediaDetails":[{"media_url_https":"https://pbs.twimg.com/thumb.jpg"}],
            "user":{"name":"S M A","screen_name":"sma","profile_image_url_https":"https://pbs.twimg.com/p/x_normal.jpg","is_blue_verified":true}}"""
        val p = PostPreviewSource.fromSyndication(json)!!
        assertEquals("Too realistic & fun 💀 #GTA6", p.text)
        assertEquals("sma", p.handle)
        assertEquals("https://pbs.twimg.com/p/x_bigger.jpg", p.avatarUrl)
        assertEquals("https://pbs.twimg.com/thumb.jpg", p.mediaUrl)
        assertTrue(p.verified)
    }

    @Test
    fun oEmbedTextWithoutPicLink() {
        val json = """{"author_name":"S M A","author_url":"https://x.com/sma",
            "html":"<blockquote><p lang=\"en\">Hi <a href=\"x\">#GTA6</a> <a href=\"t\">pic.twitter.com/abc</a></p>&mdash; S M A</blockquote>"}"""
        val p = PostPreviewSource.fromOEmbed(json)!!
        assertEquals("Hi #GTA6", p.text)
        assertEquals("sma", p.handle)
    }

    @Test
    fun relativeTime() {
        val now = 1_000_000_000_000L
        assertEquals("now", PostPreviewSource.relativeTime(now - 2_000, now, "now", Locale.US))
        assertEquals("5m", PostPreviewSource.relativeTime(now - 300_000, now, "now", Locale.US))
        assertEquals("15h", PostPreviewSource.relativeTime(now - 15 * 3_600_000L, now, "now", Locale.US))
    }
}
