package dev.grenadin.xreply

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class ReplySentCard : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var card: LinearLayout
    private var closing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.apply {
            setGravity(Gravity.TOP)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        val postUrl = intent.getStringExtra(EXTRA_URL).orEmpty()

        val title = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(ImageView(context).apply { setImageResource(R.drawable.ic_check_circle) }, LinearLayout.LayoutParams(u(42), u(42)))
            addView(TextView(context).apply {
                text = getString(R.string.status_sent)
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_PX, u(38).toFloat())
                includeFontPadding = false
                setPadding(u(24), 0, 0, 0)
            })
        }
        val viewPost = TextView(this).apply {
            text = getString(R.string.view_post)
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u(34).toFloat())
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0x26FFFFFF)
                cornerRadius = u(16).toFloat()
            }
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(postUrl)).setPackage(WebReplyActivity.X_PACKAGE))
                } catch (_: ActivityNotFoundException) {
                }
                close()
            }
        }
        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(u(30), u(34), u(30), u(28))
            background = GradientDrawable().apply {
                setColor(0xF2434F5B.toInt())
                cornerRadius = u(34).toFloat()
            }
            elevation = u(12).toFloat()
            addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, u(42)))
            addView(viewPost, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, u(63)).apply { topMargin = u(23) })
            setOnClickListener { close() }
        }
        setContentView(FrameLayout(this).apply {
            setPadding(0, u(120), 0, u(24))
            clipToPadding = false
            addView(card, FrameLayout.LayoutParams(resources.displayMetrics.widthPixels / 2, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))
        })
        card.alpha = 0f
        card.translationY = -dp(40).toFloat()
        card.animate().alpha(1f).translationY(0f).setDuration(200).start()
        handler.postDelayed(::close, SHOW_MS)
    }

    private fun close() {
        if (closing) return
        closing = true
        handler.removeCallbacksAndMessages(null)
        card.animate().alpha(0f).translationY(-dp(40).toFloat()).setDuration(180).withEndAction {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }.start()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // Sizes measured on a 900 px wide screenshot of X's own card on a 1100 px, 480 dpi screen: 1 unit = 11/27 dp.
    private fun u(value: Int) = (value * 11f / 27f * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_URL = "url"
        private const val SHOW_MS = 4900L

        fun showLater(context: Context, postUrl: String, delayMs: Long) {
            val app = context.applicationContext
            Handler(Looper.getMainLooper()).postDelayed({ show(app, postUrl) }, delayMs)
        }

        private fun show(context: Context, postUrl: String) {
            Log.i("XReply", "show reply sent card")
            context.startActivity(
                Intent(context, ReplySentCard::class.java)
                    .putExtra(EXTRA_URL, postUrl)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }
}
