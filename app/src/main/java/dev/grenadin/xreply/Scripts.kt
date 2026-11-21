package dev.grenadin.xreply

import android.content.Context

// JavaScript run inside x.com pages, kept in assets/js.
object Scripts {
    private val cache = HashMap<String, String>()

    fun get(context: Context, name: String): String = synchronized(cache) {
        cache.getOrPut(name) { context.assets.open("js/$name.js").bufferedReader().use { it.readText() } }
    }
}
