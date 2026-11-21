package dev.grenadin.xreply

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView

fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

fun View.dp(value: Int) = context.dp(value)

fun Context.themeColor(attr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return value.data
}

fun Context.circleImage(sizeDp: Int) = ImageView(this).apply {
    scaleType = ImageView.ScaleType.CENTER_CROP
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(getColor(R.color.divider))
    }
    outlineProvider = ViewOutlineProvider.BACKGROUND
    clipToOutline = true
    minimumWidth = dp(sizeDp)
    minimumHeight = dp(sizeDp)
}
