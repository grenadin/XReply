package dev.grenadin.xreply

// Decides what WebReplyActivity does next from the result of the page scripts. No Android code, so it can be unit tested.
class ReplyFlow(
    private val mediaCount: Int,
    private val fromApp: Boolean,
    private val startedAt: Long,
    private var needsFill: Boolean = false,
) {
    enum class Phase { LOADING, CLICKED, DONE, MANUAL }

    enum class Reason { NONE, LOGIN, MISMATCH, MEDIA_FAILED, UNKNOWN, TIMEOUT }

    sealed interface Action {
        data object Attach : Action
        data object Fill : Action
        data object Click : Action
        data object Reload : Action
        data class Reveal(val reason: Reason) : Action
        data class Sent(val id: String, val link: String) : Action {
            val newPostUrl: String
                get() = when {
                    link.contains("/status/") -> link
                    id.isNotEmpty() -> "https://x.com/i/status/$id"
                    else -> ""
                }
        }
    }

    var phase = Phase.LOADING
        private set
    var lastToast = ""
        private set
    private var readySince = 0L
    private var clickedAt = 0L
    private var attachedAt = 0L
    private var leftAt = 0L
    private var seenLogin = false
    private var reloads = 0
    private var filledAt = 0L
    private var fills = 0

    fun manual() {
        phase = Phase.MANUAL
    }

    fun onClicked(now: Long) {
        if (phase != Phase.LOADING) return
        phase = Phase.CLICKED
        clickedAt = now
    }

    fun onResult(result: String, now: Long, revealed: Boolean): List<Action> {
        val actions = mutableListOf<Action>()
        when (phase) {
            Phase.LOADING -> onLoading(result, now, actions)
            Phase.CLICKED -> onClickedResult(result, now, actions)
            else -> return actions
        }
        val hiddenTimeout = if (mediaCount == 0) HIDDEN_TIMEOUT_MS else HIDDEN_TIMEOUT_MEDIA_MS
        if (phase == Phase.LOADING && !revealed && now - startedAt > hiddenTimeout) actions += Action.Reveal(Reason.TIMEOUT)
        return actions
    }

    private fun onLoading(result: String, now: Long, actions: MutableList<Action>) {
        when {
            result == "login" -> {
                seenLogin = true
                readySince = 0
                actions += Action.Reveal(Reason.LOGIN)
            }
            result.startsWith("other") -> {
                readySince = 0
                if (seenLogin && reloads < MAX_RELOADS) {
                    reloads++
                    seenLogin = false
                    needsFill = false
                    actions += Action.Reload
                }
            }
            result.startsWith("mismatch") -> {
                phase = Phase.MANUAL
                actions += Action.Reveal(Reason.MISMATCH)
            }
            result == "waiting:empty" && needsFill -> {
                readySince = 0
                if (fills < MAX_FILLS && (filledAt == 0L || now - filledAt > FILL_RETRY_MS)) {
                    fills++
                    filledAt = now
                    actions += Action.Fill
                }
            }
            result == "needmedia" -> {
                readySince = 0
                if (attachedAt == 0L) {
                    attachedAt = now
                    actions += Action.Attach
                } else if (now - attachedAt > MEDIA_TIMEOUT_MS) {
                    phase = Phase.MANUAL
                    actions += Action.Reveal(Reason.MEDIA_FAILED)
                }
            }
            result == "ready" && !fromApp -> {
                phase = Phase.MANUAL
                actions += Action.Reveal(Reason.NONE)
            }
            result == "ready" -> {
                if (readySince == 0L) readySince = now
                if (now - readySince >= CLICK_DELAY_MS) actions += Action.Click
            }
            else -> readySince = 0
        }
    }

    private fun onClickedResult(result: String, now: Long, actions: MutableList<Action>) {
        when {
            result.startsWith("left:") -> {
                val parts = result.removePrefix("left:").split("|")
                val id = parts.getOrElse(1) { "" }
                val link = parts.getOrElse(2) { "" }
                if (leftAt == 0L) leftAt = now
                if (id.isEmpty() && link.isEmpty() && now - leftAt < NEW_POST_WAIT_MS) return
                phase = Phase.DONE
                actions += Action.Sent(id, link)
            }
            result.startsWith("toast:") -> lastToast = result.removePrefix("toast:")
            now - clickedAt > (if (mediaCount == 0) CONFIRM_TIMEOUT_MS else CONFIRM_TIMEOUT_MEDIA_MS) -> {
                phase = Phase.MANUAL
                actions += Action.Reveal(Reason.UNKNOWN)
            }
        }
    }

    companion object {
        const val MEDIA_TIMEOUT_MS = 10000L
        const val HIDDEN_TIMEOUT_MS = 20000L
        const val HIDDEN_TIMEOUT_MEDIA_MS = 60000L
        const val NEW_POST_WAIT_MS = 1500L
        const val CLICK_DELAY_MS = 100L
        const val CONFIRM_TIMEOUT_MS = 15000L
        const val CONFIRM_TIMEOUT_MEDIA_MS = 45000L
        const val MAX_RELOADS = 2
        const val FILL_RETRY_MS = 1500L
        const val MAX_FILLS = 2
    }
}
