package dev.grenadin.xcompanion

import java.net.URLEncoder

data class Post(val id: String, val user: String?) {
    val url: String get() = "https://x.com/${user ?: "i/web"}/status/$id"
}

object PostLink {
    private val PATTERN = Regex(
        """(?:^|[^\w.-])(?:(?:www|mobile)\.)?(?:x|twitter)\.com/(i/web|[A-Za-z0-9_]{1,15})/status(?:es)?/(\d{1,20})""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String?): Post? {
        val match = PATTERN.find(text ?: return null) ?: return null
        val user = match.groupValues[1].takeUnless { it.equals("i/web", ignoreCase = true) }
        return Post(match.groupValues[2], user)
    }

    fun replyUrl(id: String, text: String): String {
        val url = StringBuilder("https://x.com/intent/post?in_reply_to=").append(id)
        if (text.isNotBlank()) {
            url.append("&text=").append(URLEncoder.encode(text, "UTF-8").replace("+", "%20"))
        }
        return url.toString()
    }
}
