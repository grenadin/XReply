package dev.grenadin.xreply

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

class ReplyActivity : Activity() {
    private lateinit var linkInput: EditText
    private lateinit var textInput: EditText
    private lateinit var target: TextView
    private lateinit var send: Button
    private var post: Post? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(20)
        target = TextView(this)
        linkInput = EditText(this).apply {
            hint = getString(R.string.link_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        textInput = EditText(this).apply {
            hint = getString(R.string.text_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            maxLines = 8
        }
        send = Button(this).apply {
            text = getString(R.string.open_web)
            setOnClickListener { post?.let { openReply(it) } }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = getString(R.string.title)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setPadding(0, 0, 0, dp(8))
            })
            addView(target)
            addView(linkInput)
            addView(textInput)
            addView(send)
        })

        linkInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = update(PostLink.parse(s?.toString()))
        })

        val shared = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        val sharedPost = PostLink.parse(shared)
        if (savedInstanceState == null && sharedPost != null) linkInput.setText(sharedPost.url)
        else update(PostLink.parse(linkInput.text.toString()))
        (if (post != null) textInput else linkInput).requestFocus()
    }

    private fun update(newPost: Post?) {
        post = newPost
        target.text = when {
            newPost == null -> getString(R.string.no_post)
            newPost.user != null -> getString(R.string.replying_to, newPost.user)
            else -> getString(R.string.replying_to_id, newPost.id)
        }
        send.isEnabled = newPost != null
    }

    private fun openReply(post: Post) {
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
    }
}
