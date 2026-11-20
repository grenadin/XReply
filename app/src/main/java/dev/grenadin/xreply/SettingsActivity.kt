package dev.grenadin.xreply

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var loginStatus: TextView
    private lateinit var clearButton: TextView
    private lateinit var loginButton: TextView
    private lateinit var autoPostSwitch: Switch
    private lateinit var autoPostHint: TextView
    private lateinit var postStatusSwitch: Switch
    private lateinit var postStatusHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(ReplyActivity.PREFS, MODE_PRIVATE)
        val textColor = getColor(R.color.text)
        val secondary = getColor(R.color.text_secondary)
        val pad = dp(16)

        loginStatus = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp(4), 0, dp(12))
        }
        clearButton = TextView(this).apply {
            text = getString(R.string.clear_login)
            setTextColor(0xFFF4212E.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(10), dp(20), dp(10))
            background = GradientDrawable().apply {
                setStroke(dp(1), 0xFFF4212E.toInt())
                cornerRadius = dp(20).toFloat()
            }
            setOnClickListener { confirmClear() }
        }
        loginButton = TextView(this).apply {
            text = getString(R.string.login_button)
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(10), dp(20), dp(10))
            background = GradientDrawable().apply {
                setColor(getColor(R.color.accent))
                cornerRadius = dp(20).toFloat()
            }
            setOnClickListener { startActivity(Intent(this@SettingsActivity, LoginActivity::class.java)) }
        }
        autoPostSwitch = Switch(this).apply {
            text = getString(R.string.auto_post)
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            isChecked = prefs.getBoolean(ReplyActivity.PREF_AUTO_POST, true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(ReplyActivity.PREF_AUTO_POST, checked).apply()
                refresh()
            }
        }
        autoPostHint = TextView(this).apply {
            setTextColor(secondary)
            setPadding(0, dp(4), 0, 0)
        }
        postStatusSwitch = Switch(this).apply {
            text = getString(R.string.show_post_status)
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            isChecked = prefs.getBoolean(ReplyActivity.PREF_SHOW_POST_STATUS, true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(ReplyActivity.PREF_SHOW_POST_STATUS, checked).apply()
                refresh()
            }
        }
        postStatusHint = TextView(this).apply {
            setTextColor(secondary)
            setPadding(0, dp(4), 0, 0)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = getString(R.string.app_name)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = getString(R.string.version, packageManager.getPackageInfo(packageName, 0).versionName)
                setTextColor(secondary)
            })
            addView(section(R.string.settings_account))
            addView(loginStatus)
            addView(loginButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(clearButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(section(R.string.settings_sending))
            addView(autoPostSwitch, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(autoPostHint)
            addView(postStatusSwitch, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
            addView(postStatusHint)
            addView(section(R.string.settings_how))
            addView(TextView(context).apply {
                text = getString(R.string.settings_how_text)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setLineSpacing(dp(4).toFloat(), 1f)
            })
            addView(TextView(context).apply {
                text = getString(R.string.unofficial)
                setTextColor(secondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(32), 0, 0)
            })
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(getColor(R.color.bg))
            addView(content)
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val loggedIn = XLogin.isLoggedIn()
        loginStatus.text = getString(if (loggedIn) R.string.settings_logged_in else R.string.settings_logged_out)
        loginStatus.setTextColor(if (loggedIn) 0xFF00BA7C.toInt() else getColor(R.color.text_secondary))
        clearButton.visibility = if (loggedIn) View.VISIBLE else View.GONE
        loginButton.visibility = if (loggedIn) View.GONE else View.VISIBLE
        autoPostHint.text = getString(if (autoPostSwitch.isChecked) R.string.auto_post_on else R.string.auto_post_off)
        postStatusSwitch.isEnabled = autoPostSwitch.isChecked
        postStatusHint.text = getString(if (postStatusSwitch.isChecked) R.string.show_post_status_on else R.string.show_post_status_off)
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setMessage(R.string.clear_login_confirm)
            .setPositiveButton(R.string.clear_login) { _, _ ->
                XLogin.clear(this)
                Toast.makeText(this, R.string.logged_out, Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun section(title: Int) = TextView(this).apply {
        text = getString(title)
        setTextColor(getColor(R.color.text))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(28), 0, dp(4))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
