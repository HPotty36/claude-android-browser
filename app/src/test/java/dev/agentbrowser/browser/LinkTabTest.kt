package dev.agentbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTabTest {
    @Test fun aBlankTabTakesTheLink() = assertFalse(linkNeedsNewTab(shownUrl = HOME_URL, waitingUrl = null))
    @Test fun aTabThatHasShownNothingYetTakesTheLink() = assertFalse(linkNeedsNewTab(shownUrl = "", waitingUrl = null))
    @Test fun aPageKeepsItsTab() = assertTrue(linkNeedsNewTab(shownUrl = "https://example.com/", waitingUrl = null))

    // At start-up the first link waits for the engine; a second link must not take its place.
    @Test fun aLinkStillWaitingToOpenKeepsItsTab() =
        assertTrue(linkNeedsNewTab(shownUrl = "", waitingUrl = "https://example.com/first"))
}
