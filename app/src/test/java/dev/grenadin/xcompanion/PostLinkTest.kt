package dev.grenadin.xcompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostLinkTest {
    @Test
    fun parsesShareTexts() {
        assertEquals(Post("1234567890123456789", "someone"), PostLink.parse("https://x.com/someone/status/1234567890123456789?s=20"))
        assertEquals(Post("42", "a_b"), PostLink.parse("Look at this https://twitter.com/a_b/status/42 ok"))
        assertEquals(Post("42", "a_b"), PostLink.parse("https://mobile.twitter.com/a_b/statuses/42"))
        assertEquals(Post("42", null), PostLink.parse("https://x.com/i/web/status/42"))
        assertEquals(Post("42", "Someone"), PostLink.parse("HTTPS://X.COM/Someone/status/42/photo/1"))
    }

    @Test
    fun rejectsOtherLinks() {
        assertNull(PostLink.parse(null))
        assertNull(PostLink.parse("hello"))
        assertNull(PostLink.parse("https://fox.com/a/status/42"))
        assertNull(PostLink.parse("https://x.com/someone"))
        assertNull(PostLink.parse("https://x.com.evil.example/a/status/42"))
    }

    @Test
    fun buildsReplyUrl() {
        assertEquals("https://x.com/intent/post?in_reply_to=42", PostLink.replyUrl("42", " "))
        assertEquals("https://x.com/intent/post?in_reply_to=42&text=a%20b%26c%23d", PostLink.replyUrl("42", "a b&c#d"))
        assertEquals("https://x.com/intent/post?in_reply_to=42&text=%E0%B8%AA%E0%B8%A7%E0%B8%B1%E0%B8%AA%E0%B8%94%E0%B8%B5%20%F0%9F%98%80",
            PostLink.replyUrl("42", "สวัสดี 😀"))
    }
}
