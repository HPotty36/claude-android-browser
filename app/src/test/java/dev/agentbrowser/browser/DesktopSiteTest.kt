package dev.agentbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSiteTest {
    @Test fun aPhoneGetsTheMobileSite() = assertFalse(wantsDesktopSite(DesktopSite.AUTO, smallestWidthDp = 411))
    @Test fun aTabletSizedScreenGetsTheDesktopSite() = assertTrue(wantsDesktopSite(DesktopSite.AUTO, smallestWidthDp = 600))
    @Test fun justUnderTabletSizeIsStillMobile() = assertFalse(wantsDesktopSite(DesktopSite.AUTO, smallestWidthDp = 599))
    @Test fun alwaysWinsOnAPhone() = assertTrue(wantsDesktopSite(DesktopSite.ALWAYS, smallestWidthDp = 360))
    @Test fun neverWinsOnATablet() = assertFalse(wantsDesktopSite(DesktopSite.NEVER, smallestWidthDp = 1280))
    @Test fun unknownSavedValueMeansAuto() = assertEquals(DesktopSite.AUTO, desktopSiteById("nope"))
    @Test fun savedValueRoundTrips() = DesktopSite.entries.forEach { assertEquals(it, desktopSiteById(it.id)) }

    @Test fun aMobileAddressHasADesktopOne() {
        assertEquals("https://naver.com/", desktopUrl("https://m.naver.com/"))
        assertEquals("https://blog.naver.com/someone/123?x=1#top", desktopUrl("https://m.blog.naver.com/someone/123?x=1#top"))
        assertEquals("https://en.wikipedia.org/wiki/Seoul", desktopUrl("https://en.m.wikipedia.org/wiki/Seoul"))
        assertEquals("https://twitter.com/home", desktopUrl("https://mobile.twitter.com/home"))
        assertEquals("http://test.localhost:8080/a", desktopUrl("http://m.test.localhost:8080/a"))
        assertEquals("https://youtube.com/watch?v=1", desktopUrl("HTTPS://M.YouTube.com/watch?v=1"))
    }

    @Test fun anOrdinaryAddressIsLeftAlone() {
        listOf("https://www.naver.com/", "https://example.com/m/page", "https://mail.google.com/", "https://m.kr/", "about:blank",
            "https://example.com/?next=https://m.naver.com/", "not a url", "")
            .forEach { assertNull(it, desktopUrl(it)) }
    }

    @Test fun aMonitorIsMeasuredByItsShorterSide() = assertEquals(1080, smallestSideDp(1920, 1080, density = 1f))
    @Test fun aPhonesPixelsAreScaledDown() = assertEquals(411, smallestSideDp(2400, 1080, density = 2.625f))
    @Test fun aScreenWithNoDensityCountsAsSmall() = assertEquals(0, smallestSideDp(1920, 1080, density = 0f))
}
