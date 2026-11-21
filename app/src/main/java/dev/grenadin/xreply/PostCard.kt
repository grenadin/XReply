package dev.grenadin.xreply

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

// The post being replied to, drawn like X's reply composer: avatar and thread line on the left, name, text and photo on the right.
class PostCard(context: Context) : LinearLayout(context) {
    private val textColor = context.getColor(R.color.text)
    private val secondary = context.getColor(R.color.text_secondary)
    private val accent = context.getColor(R.color.accent)

    private val avatar = context.circleImage(40)
    private val nameView = TextView(context).apply {
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTypeface(typeface, Typeface.BOLD)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        maxWidth = (resources.displayMetrics.widthPixels * 0.45f).toInt()
    }
    private val verifiedView = ImageView(context).apply {
        setImageResource(R.drawable.ic_verified)
        visibility = View.GONE
        setPadding(dp(2), 0, dp(2), 0)
    }
    private val metaView = TextView(context).apply {
        setTextColor(secondary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    }
    private val bodyView = TextView(context).apply {
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setPadding(0, dp(2), 0, 0)
        maxLines = 8
        ellipsize = TextUtils.TruncateAt.END
    }
    private val mediaView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = View.GONE
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) =
                outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
        }
        clipToOutline = true
    }
    private val replyingTo = TextView(context).apply {
        setTextColor(secondary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setPadding(0, dp(16), 0, dp(8))
    }

    init {
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(nameView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { weight = 0f })
            addView(verifiedView)
            addView(metaView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        val textColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(header)
            addView(bodyView)
        }
        val postRow = LinearLayout(context).apply {
            addView(textColumn, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(mediaView, LayoutParams(dp(96), dp(96)).apply { marginStart = dp(12); topMargin = dp(4) })
        }
        val rightColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(postRow)
            addView(replyingTo)
        }
        val leftColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(avatar, LayoutParams(dp(40), dp(40)))
            addView(View(context).apply { setBackgroundColor(context.getColor(R.color.divider)) },
                LayoutParams(dp(2), 0, 1f).apply { topMargin = dp(4) })
        }
        addView(leftColumn, LayoutParams(dp(40), LayoutParams.MATCH_PARENT))
        addView(rightColumn, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
    }

    fun showLoading(handle: String?) {
        nameView.text = handle?.let { "@$it" }.orEmpty()
        verifiedView.visibility = View.GONE
        metaView.text = ""
        bodyView.text = ""
        avatar.setImageBitmap(null)
        mediaView.visibility = View.GONE
    }

    fun show(preview: PostPreview) {
        nameView.text = preview.name.ifEmpty { "@${preview.handle}" }
        verifiedView.visibility = if (preview.verified) View.VISIBLE else View.GONE
        val time = preview.createdAt?.let {
            PostPreviewSource.relativeTime(it, System.currentTimeMillis(), context.getString(R.string.time_now), Locale.getDefault())
        }
        metaView.text = listOfNotNull(" @${preview.handle}", time).joinToString(" · ")
        bodyView.text = highlight(preview.text)
    }

    fun setAvatar(bitmap: Bitmap) = avatar.setImageBitmap(bitmap)

    fun setMedia(bitmap: Bitmap) {
        mediaView.setImageBitmap(bitmap)
        mediaView.visibility = View.VISIBLE
    }

    fun setReplyingTo(handle: String?, postId: String) {
        if (handle == null) {
            replyingTo.text = context.getString(R.string.replying_to_id, postId)
            return
        }
        val label = context.getString(R.string.replying_to, handle)
        val at = label.indexOf("@$handle")
        replyingTo.text = SpannableString(label).apply {
            if (at >= 0) setSpan(ForegroundColorSpan(accent), at, at + handle.length + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun highlight(text: String): CharSequence = SpannableString(text).apply {
        for (match in TAG_PATTERN.findAll(text)) {
            setSpan(ForegroundColorSpan(accent), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    companion object {
        private val TAG_PATTERN = Regex("""(?<![\w@#])[#@][\p{L}\p{N}_]+""")
    }
}
