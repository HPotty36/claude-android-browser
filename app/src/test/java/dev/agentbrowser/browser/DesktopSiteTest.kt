package dev.agentbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
