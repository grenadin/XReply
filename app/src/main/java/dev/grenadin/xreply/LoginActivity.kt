package dev.grenadin.xreply

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class LoginActivity : Activity() {
    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = XWebView.create(this, xOnly = false)
        val close = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            background = null
            contentDescription = getString(R.string.cancel)
            setOnClickListener { finish() }
        }
        val title = TextView(this).apply {
            text = getString(R.string.login_title)
            setTextColor(getColor(R.color.text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setBackgroundColor(getColor(R.color.bg))
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(4), dp(16), dp(4))
                addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(title)
            })
            addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        if (savedInstanceState == null) webView.loadUrl(LOGIN_URL) else webView.restoreState(savedInstanceState)
        handler.postDelayed(::check, CHECK_MS)
    }

    private fun check() {
        if (isFinishing) return
        if (XLogin.isLoggedIn()) {
            CookieManager.getInstance().flush()
            Toast.makeText(this, R.string.settings_logged_in, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        handler.postDelayed(::check, CHECK_MS)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val LOGIN_URL = "https://x.com/i/flow/login"
        private const val CHECK_MS = 500L
    }
}
