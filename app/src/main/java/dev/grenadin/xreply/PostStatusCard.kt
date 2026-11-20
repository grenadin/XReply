package dev.grenadin.xreply

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class PostStatusCard(
    context: Context,
    backgroundColor: Int,
    onCancel: () -> Unit,
    onShowPage: () -> Unit,
) : LinearLayout(context) {
    private val status = TextView(context).apply {
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, dp(12))
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(24), dp(24), dp(24), dp(16))
        background = GradientDrawable().apply {
            setColor(backgroundColor)
            cornerRadius = dp(16).toFloat()
        }
        elevation = dp(8).toFloat()
        addView(ProgressBar(context))
        addView(status)
        addView(LinearLayout(context).apply {
            addView(Button(context, null, android.R.attr.borderlessButtonStyle).apply {
                text = context.getString(R.string.cancel)
                setOnClickListener { onCancel() }
            })
            addView(Button(context, null, android.R.attr.borderlessButtonStyle).apply {
                text = context.getString(R.string.show_page)
                setOnClickListener { onShowPage() }
            })
        })
    }

    fun setStatus(text: CharSequence) {
        status.text = text
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
