package dev.grenadin.xreply

import dev.grenadin.xreply.ReplyFlow.Action
import dev.grenadin.xreply.ReplyFlow.Phase
import dev.grenadin.xreply.ReplyFlow.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyFlowTest {
    private fun flow(media: Int = 0, fromApp: Boolean = true) = ReplyFlow(media, fromApp, startedAt = 0)

    @Test
    fun clicksOnlyAfterReadyForClickDelay() {
        val f = flow()
        assertEquals(emptyList<Action>(), f.onResult("waiting:nobtn", 1000, false))
        assertEquals(emptyList<Action>(), f.onResult("ready", 2000, false))
        assertEquals(emptyList<Action>(), f.onResult("ready", 2000 + ReplyFlow.CLICK_DELAY_MS - 1, false))
        assertEquals(listOf(Action.Click), f.onResult("ready", 2000 + ReplyFlow.CLICK_DELAY_MS, false))
    }

    @Test
    fun notReadyResetsClickDelay() {
        val f = flow()
        f.onResult("ready", 1000, false)
        f.onResult("waiting:disabled", 1200, false)
        assertEquals(emptyList<Action>(), f.onResult("ready", 1400, false))
    }

    @Test
    fun fullSendFromRealLog() {
        val f = flow()
        f.onResult("other:blank", 264, false)
        f.onResult("waiting:noboxnobtn", 761, false)
        f.onResult("ready", 3269, false)
        assertEquals(listOf(Action.Click), f.onResult("ready", 3569, false))
        f.onClicked(3700)
        assertEquals(Phase.CLICKED, f.phase)
        assertEquals(emptyList<Action>(), f.onResult("waiting", 4022, false))
        val actions = f.onResult("left:/home|2123711006195261892|https://x.com/someone/status/2123711006195261892", 5375, false)
        assertEquals(listOf(Action.Sent("2123711006195261892", "https://x.com/someone/status/2123711006195261892")), actions)
        assertEquals("https://x.com/someone/status/2123711006195261892", (actions[0] as Action.Sent).newPostUrl)
        assertEquals(Phase.DONE, f.phase)
        assertEquals(emptyList<Action>(), f.onResult("waiting", 5600, false))
    }

    @Test
    fun waitsForNewPostIdAfterLeaving() {
        val f = flow()
        f.onClicked(0)
        assertEquals(emptyList<Action>(), f.onResult("left:/home||", 1000, false))
        assertEquals(emptyList<Action>(), f.onResult("left:/home||", 1000 + ReplyFlow.NEW_POST_WAIT_MS - 1, false))
        val actions = f.onResult("left:/home||", 1000 + ReplyFlow.NEW_POST_WAIT_MS, false)
        assertEquals(listOf(Action.Sent("", "")), actions)
        assertEquals("", (actions[0] as Action.Sent).newPostUrl)
    }

    @Test
    fun idWithoutToastLinkBuildsStatusUrl() {
        val f = flow()
        f.onClicked(0)
        val sent = f.onResult("left:/home|42|", 500, false).single() as Action.Sent
        assertEquals("https://x.com/i/status/42", sent.newPostUrl)
    }

    @Test
    fun loginRevealsAndReloadsAfterLogin() {
        val f = flow()
        assertEquals(listOf(Action.Reveal(Reason.LOGIN)), f.onResult("login", 500, false))
        assertEquals(listOf(Action.Reload), f.onResult("other:/home", 9000, true))
        assertEquals(emptyList<Action>(), f.onResult("other:/home", 9500, true))
    }

    @Test
    fun reloadsAtMostTwice() {
        val f = flow()
        repeat(ReplyFlow.MAX_RELOADS) {
            f.onResult("login", 100, true)
            assertEquals(listOf(Action.Reload), f.onResult("other:/home", 200, true))
        }
        f.onResult("login", 300, true)
        assertEquals(emptyList<Action>(), f.onResult("other:/home", 400, true))
    }

    @Test
    fun mismatchStopsAndShowsPage() {
        val f = flow()
        assertEquals(listOf(Action.Reveal(Reason.MISMATCH)), f.onResult("mismatch:[\"a\"] vs \"b\"", 2000, false))
        assertEquals(Phase.MANUAL, f.phase)
        assertEquals(emptyList<Action>(), f.onResult("ready", 3000, true))
    }

    @Test
    fun notFromAppNeverClicks() {
        val f = flow(fromApp = false)
        assertEquals(listOf(Action.Reveal(Reason.NONE)), f.onResult("ready", 2000, false))
        assertEquals(Phase.MANUAL, f.phase)
    }

    @Test
    fun attachesMediaOnceThenTimesOut() {
        val f = flow(media = 2)
        assertEquals(listOf(Action.Attach), f.onResult("needmedia", 2000, false))
        assertEquals(emptyList<Action>(), f.onResult("needmedia", 2000 + ReplyFlow.MEDIA_TIMEOUT_MS, false))
        assertEquals(listOf(Action.Reveal(Reason.MEDIA_FAILED)), f.onResult("needmedia", 2001 + ReplyFlow.MEDIA_TIMEOUT_MS, false))
        assertEquals(Phase.MANUAL, f.phase)
    }

    @Test
    fun hiddenTimeoutRevealsOnlyWhileHidden() {
        val f = flow()
        assertEquals(listOf(Action.Reveal(Reason.TIMEOUT)), f.onResult("waiting:nobox", ReplyFlow.HIDDEN_TIMEOUT_MS + 1, false))
        assertEquals(emptyList<Action>(), f.onResult("waiting:empty", ReplyFlow.HIDDEN_TIMEOUT_MS + 2, true))
        val withMedia = flow(media = 1)
        assertEquals(emptyList<Action>(), withMedia.onResult("waiting:upload", ReplyFlow.HIDDEN_TIMEOUT_MS + 1, false))
    }

    @Test
    fun noConfirmationKeepsLastToast() {
        val f = flow()
        f.onClicked(1000)
        f.onResult("toast:Something went wrong", 2000, false)
        assertEquals(listOf(Action.Reveal(Reason.UNKNOWN)), f.onResult("waiting", 1001 + ReplyFlow.CONFIRM_TIMEOUT_MS, false))
        assertEquals("Something went wrong", f.lastToast)
        assertEquals(Phase.MANUAL, f.phase)
    }

    @Test
    fun preloadedPageFillsEmptyBoxOnceThenRetries() {
        val f = ReplyFlow(0, true, startedAt = 0, needsFill = true)
        assertEquals(listOf(Action.Fill), f.onResult("waiting:empty", 100, false))
        assertEquals(emptyList<Action>(), f.onResult("waiting:empty", 100 + ReplyFlow.FILL_RETRY_MS, false))
        assertEquals(listOf(Action.Fill), f.onResult("waiting:empty", 101 + ReplyFlow.FILL_RETRY_MS, false))
        assertEquals(emptyList<Action>(), f.onResult("waiting:empty", 10000, false))
    }

    @Test
    fun pageLoadedWithTextNeverFills() {
        assertEquals(emptyList<Action>(), flow().onResult("waiting:empty", 100, false))
        val f = ReplyFlow(0, true, startedAt = 0, needsFill = true)
        f.onResult("login", 100, false)
        f.onResult("other:/home", 200, true)
        assertEquals(emptyList<Action>(), f.onResult("waiting:empty", 300, true))
    }

    @Test
    fun clickResultIgnoredOutsideLoading() {
        val f = flow()
        f.manual()
        f.onClicked(100)
        assertEquals(Phase.MANUAL, f.phase)
        assertTrue(f.onResult("ready", 5000, true).isEmpty())
    }
}
