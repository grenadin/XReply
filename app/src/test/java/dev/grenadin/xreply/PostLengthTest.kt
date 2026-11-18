package dev.grenadin.xreply

import org.junit.Assert.assertEquals
import org.junit.Test

class PostLengthTest {
    @Test
    fun countsLatinAndThaiAsOne() {
        assertEquals(5, PostLength.count("hello"))
        assertEquals(6, PostLength.count("สวัสดี"))
        assertEquals(1, PostLength.count("é"))
    }

    @Test
    fun countsCjkAndEmojiAsTwo() {
        assertEquals(4, PostLength.count("日本"))
        assertEquals(2, PostLength.count("😀"))
        assertEquals(2, PostLength.count("👍🏽"))
        assertEquals(2, PostLength.count("👨‍👩‍👧"))
        assertEquals(2, PostLength.count("❤️"))
        assertEquals(2, PostLength.count("🇹🇭"))
        assertEquals(2, PostLength.count("1️⃣"))
        assertEquals(3, PostLength.count("a😀"))
        assertEquals(1, PostLength.count("#"))
    }

    @Test
    fun countsUrlsAs23() {
        assertEquals(25, PostLength.count("a https://example.com/a/very/long/path?query=1"))
        assertEquals(25, PostLength.count("www.example.com x"))
    }
}
