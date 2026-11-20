package dev.grenadin.xreply

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class ReplyActivity : Activity() {
    private lateinit var textInput: EditText
    private lateinit var send: TextView
    private lateinit var counter: TextView
    private lateinit var modeLabel: TextView
    private lateinit var card: LinearLayout
    private lateinit var replyRow: LinearLayout
    private lateinit var avatar: ImageView
    private lateinit var nameView: TextView
    private lateinit var verifiedView: ImageView
    private lateinit var metaView: TextView
    private lateinit var bodyView: TextView
    private lateinit var mediaView: ImageView
    private lateinit var replyingTo: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var post: Post? = null
    private var previewFor: String? = null
    private var autoPost = true
    private val media = mutableListOf<File>()
    private lateinit var mediaRow: LinearLayout
    private lateinit var galleryButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        val sharedPost = PostLink.parse(shared)
        if (sharedPost == null) {
            Toast.makeText(this, R.string.no_post, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }
        if (savedInstanceState == null) MediaFiles.clearOld(this)
        savedInstanceState?.getStringArrayList(STATE_MEDIA)?.map(::File)?.filter { it.exists() }?.let(media::addAll)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        autoPost = prefs.getBoolean(PREF_AUTO_POST, true)
        val textColor = getColor(R.color.text)
        val secondary = getColor(R.color.text_secondary)
        val pad = dp(16)

        val close = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            background = null
            contentDescription = getString(R.string.cancel)
            setOnClickListener { finish() }
        }
        val more = ImageButton(this).apply {
            setImageResource(R.drawable.ic_more)
            background = null
            contentDescription = getString(R.string.more_options)
        }
        more.setOnClickListener { showMenu(more, prefs) }
        send = TextView(this).apply {
            text = getString(R.string.reply_button)
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(18), dp(8), dp(18), dp(8))
            background = GradientDrawable().apply {
                setColor(getColor(R.color.accent))
                cornerRadius = dp(20).toFloat()
            }
            setOnClickListener { post?.let { openReply(it) } }
        }
        val topBar = FrameLayout(this).apply {
            setPadding(dp(4), dp(4), dp(12), dp(4))
            addView(close, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START or Gravity.CENTER_VERTICAL))
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(more, LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(send)
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL))
        }
        modeLabel = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(pad, 0, pad, dp(8))
        }

        avatar = circleImage(40)
        nameView = TextView(this).apply {
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        verifiedView = ImageView(this).apply {
            setImageResource(R.drawable.ic_verified)
            visibility = View.GONE
            setPadding(dp(2), 0, dp(2), 0)
        }
        metaView = TextView(this).apply {
            setTextColor(secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        bodyView = TextView(this).apply {
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp(2), 0, 0)
            maxLines = 8
            ellipsize = TextUtils.TruncateAt.END
        }
        mediaView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
            }
            clipToOutline = true
        }
        replyingTo = TextView(this).apply {
            setTextColor(secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp(16), 0, dp(8))
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(nameView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { weight = 0f })
            addView(verifiedView)
            addView(metaView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        nameView.maxWidth = (resources.displayMetrics.widthPixels * 0.45f).toInt()
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(bodyView)
        }
        val postRow = LinearLayout(this).apply {
            addView(textColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(mediaView, LinearLayout.LayoutParams(dp(96), dp(96)).apply { marginStart = dp(12); topMargin = dp(4) })
        }
        val rightColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(postRow)
            addView(replyingTo)
        }
        val leftColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(avatar, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(View(context).apply { setBackgroundColor(getColor(R.color.divider)) },
                LinearLayout.LayoutParams(dp(2), 0, 1f).apply { topMargin = dp(4) })
        }
        card = LinearLayout(this).apply {
            addView(leftColumn, LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.MATCH_PARENT))
            addView(rightColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
        }

        textInput = EditText(this).apply {
            hint = getString(R.string.post_reply_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            background = null
            setPadding(0, dp(6), 0, dp(6))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(textColor)
            setHintTextColor(secondary)
        }
        replyRow = LinearLayout(this).apply {
            addView(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(getColor(R.color.divider))
                }
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(textInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
        }
        mediaRow = LinearLayout(this)
        val mediaScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(mediaRow)
        }
        galleryButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_image)
            background = null
            contentDescription = getString(R.string.add_image)
            setOnClickListener { pickImages() }
        }
        val toolBar = LinearLayout(this).apply {
            setPadding(dp(4), 0, dp(4), 0)
            addView(galleryButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        counter = TextView(this).apply {
            gravity = Gravity.END
            setTextColor(secondary)
            setPadding(0, dp(4), 0, dp(4))
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(4), pad, pad)
            addView(card)
            addView(replyRow)
            addView(mediaScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(52) })
            addView(counter)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setBackgroundColor(getColor(R.color.bg))
            addView(topBar)
            addView(modeLabel)
            addView(ScrollView(context).apply {
                isFillViewport = true
                addView(content)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(View(context).apply { setBackgroundColor(getColor(R.color.divider)) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
            addView(toolBar)
        })
        showMedia()

        textInput.addTextChangedListener(watcher { updateCounter() })

        update(sharedPost)
        updateCounter()
        textInput.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        updateMode()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun showMenu(anchor: View, prefs: android.content.SharedPreferences) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_AUTO, 0, R.string.auto_post).apply {
                isCheckable = true
                isChecked = autoPost
            }
            menu.add(0, MENU_LOGOUT, 1, R.string.logout)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_AUTO -> {
                        autoPost = !autoPost
                        prefs.edit().putBoolean(PREF_AUTO_POST, autoPost).apply()
                    }
                    MENU_LOGOUT -> {
                        XLogin.clear(this@ReplyActivity)
                        Toast.makeText(this@ReplyActivity, R.string.logged_out, Toast.LENGTH_SHORT).show()
                    }
                }
                updateMode()
                true
            }
        }.show()
    }

    private fun updateMode() {
        if (autoPost) {
            val loggedIn = XLogin.isLoggedIn()
            modeLabel.text = getString(R.string.mode_webview,
                getString(if (loggedIn) R.string.webview_logged_in else R.string.webview_logged_out))
            modeLabel.setTextColor(if (loggedIn) 0xFF00BA7C.toInt() else 0xFFEF6C00.toInt())
        } else {
            modeLabel.text = getString(R.string.mode_chrome)
            modeLabel.setTextColor(getColor(R.color.text_secondary))
        }
        galleryButton.alpha = if (autoPost && media.size < MediaFiles.MAX_IMAGES) 1f else 0.4f
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_MEDIA, ArrayList(media.map { it.path }))
    }

    private fun updateCounter() {
        val length = PostLength.count(textInput.text.toString())
        counter.text = getString(R.string.counter, length, PostLength.LIMIT)
        counter.setTextColor(if (length > PostLength.LIMIT) 0xFFF4212E.toInt() else getColor(R.color.text_secondary))
        updateSend()
    }

    private fun updateSend() {
        val length = PostLength.count(textInput.text.toString())
        val enabled = post != null && textInput.text.isNotBlank() && length <= PostLength.LIMIT
        send.isEnabled = enabled
        send.alpha = if (enabled) 1f else 0.5f
    }

    private fun update(newPost: Post) {
        post = newPost
        setReplyingTo(newPost.user)
        if (previewFor != newPost.id) loadPreview(newPost)
        updateSend()
    }

    private fun setReplyingTo(handle: String?) {
        if (handle == null) {
            replyingTo.text = getString(R.string.replying_to_id, post?.id.orEmpty())
            return
        }
        val label = getString(R.string.replying_to, handle)
        val at = label.indexOf("@$handle")
        replyingTo.text = SpannableString(label).apply {
            if (at >= 0) setSpan(ForegroundColorSpan(getColor(R.color.accent)), at, at + handle.length + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun loadPreview(target: Post) {
        previewFor = target.id
        nameView.text = target.user?.let { "@$it" }.orEmpty()
        verifiedView.visibility = View.GONE
        metaView.text = ""
        bodyView.text = ""
        avatar.setImageBitmap(null)
        mediaView.visibility = View.GONE
        executor.execute {
            val preview = PostPreviewSource.fetch(target)
            main.post { if (!isDestroyed && previewFor == target.id && preview != null) showPreview(target, preview) }
            val avatarBitmap = preview?.avatarUrl?.let { PostPreviewSource.loadBitmap(it, dp(40)) }
            val mediaBitmap = preview?.mediaUrl?.let { PostPreviewSource.loadBitmap(it, dp(96)) }
            main.post {
                if (isDestroyed || previewFor != target.id) return@post
                avatarBitmap?.let(avatar::setImageBitmap)
                mediaBitmap?.let(::showMedia)
            }
        }
    }

    private fun showPreview(target: Post, preview: PostPreview) {
        nameView.text = preview.name.ifEmpty { "@${preview.handle}" }
        verifiedView.visibility = if (preview.verified) View.VISIBLE else View.GONE
        val time = preview.createdAt?.let {
            PostPreviewSource.relativeTime(it, System.currentTimeMillis(), getString(R.string.time_now), Locale.getDefault())
        }
        metaView.text = listOfNotNull(" @${preview.handle}", time).joinToString(" · ")
        bodyView.text = highlight(preview.text)
        if (target.user == null && preview.handle.isNotEmpty()) {
            post = target.copy(user = preview.handle)
            setReplyingTo(preview.handle)
        }
    }

    private fun showMedia(bitmap: Bitmap) {
        mediaView.setImageBitmap(bitmap)
        mediaView.visibility = View.VISIBLE
    }

    private fun highlight(text: String): CharSequence = SpannableString(text).apply {
        for (match in TAG_PATTERN.findAll(text)) {
            setSpan(ForegroundColorSpan(getColor(R.color.accent)), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun circleImage(sizeDp: Int) = ImageView(this).apply {
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

    private fun watcher(onChange: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onChange(s?.toString().orEmpty())
    }

    private fun pickImages() {
        if (!autoPost) {
            Toast.makeText(this, R.string.images_need_auto, Toast.LENGTH_LONG).show()
            return
        }
        if (media.size >= MediaFiles.MAX_IMAGES) return
        val pick = Intent(Intent.ACTION_GET_CONTENT)
            .setType("image/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_MIME_TYPES, MediaFiles.PICK_TYPES)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        try {
            startActivityForResult(pick, REQUEST_IMAGES)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.image_failed, Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMAGES || resultCode != RESULT_OK || data == null) return
        val uris = data.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } }
            ?: listOfNotNull(data.data)
        val wanted = uris.take(MediaFiles.MAX_IMAGES - media.size)
        executor.execute {
            val files = wanted.map { MediaFiles.import(this, it) }
            main.post {
                if (isDestroyed) return@post
                if (files.any { it == null }) Toast.makeText(this, R.string.image_failed, Toast.LENGTH_SHORT).show()
                media.addAll(files.filterNotNull().take(MediaFiles.MAX_IMAGES - media.size))
                showMedia()
            }
        }
    }

    private fun showMedia() {
        mediaRow.removeAllViews()
        for (file in media) {
            val thumb = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(PostPreviewSource.decodeFile(file, dp(120)))
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) =
                        outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
                }
                clipToOutline = true
            }
            val remove = ImageButton(this).apply {
                setImageResource(R.drawable.ic_remove)
                background = null
                contentDescription = getString(R.string.remove_image)
                setOnClickListener {
                    media.remove(file)
                    file.delete()
                    showMedia()
                }
            }
            mediaRow.addView(FrameLayout(this).apply {
                addView(thumb, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                addView(remove, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP or Gravity.END))
            }, LinearLayout.LayoutParams(dp(120), dp(120)).apply { marginEnd = dp(8); topMargin = dp(8) })
        }
        updateMode()
    }

    private fun openReply(post: Post) {
        if (!autoPost && media.isNotEmpty()) {
            Toast.makeText(this, R.string.images_need_auto, Toast.LENGTH_LONG).show()
            return
        }
        if (autoPost) {
            startActivity(Intent(this, WebReplyActivity::class.java)
                .putExtra(WebReplyActivity.EXTRA_ID, post.id)
                .putExtra(WebReplyActivity.EXTRA_USER, post.user)
                .putExtra(WebReplyActivity.EXTRA_FROM_APP, true)
                .putStringArrayListExtra(WebReplyActivity.EXTRA_MEDIA, ArrayList(media.map { it.path }))
                .putExtra(WebReplyActivity.EXTRA_TEXT, textInput.text.toString()))
            finish()
            return
        }
        val uri = Uri.parse(PostLink.replyUrl(post.id, textInput.text.toString()))
        val browser = browserPackage()
        if (browser == null) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show()
            return
        }
        val tabs = CustomTabsIntent.Builder().setShowTitle(true).build()
        tabs.intent.setPackage(browser)
        try {
            tabs.launchUrl(this, uri)
            finish()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show()
        }
    }

    private fun browserPackage(): String? {
        val chromeEnabled = try {
            packageManager.getApplicationInfo(CHROME, 0).enabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        return if (chromeEnabled) CHROME else CustomTabsClient.getPackageName(this, emptyList())
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CHROME = "com.android.chrome"
        const val PREFS = "settings"
        const val PREF_AUTO_POST = "auto_post"
        const val PREF_SHOW_POST_STATUS = "show_post_status"
        private const val STATE_MEDIA = "media"
        private const val REQUEST_IMAGES = 1
        private const val MENU_AUTO = 1
        private const val MENU_LOGOUT = 2
        private val TAG_PATTERN = Regex("""(?<![\w@#])[#@][\p{L}\p{N}_]+""")
    }
}
