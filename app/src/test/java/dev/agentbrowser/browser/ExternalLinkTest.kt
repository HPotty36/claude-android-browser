package dev.agentbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLinkTest {
    @Test fun webPagesStayInTheBrowser() {
        listOf("https://example.com/a", "http://example.com", "HTTPS://EXAMPLE.COM", "about:blank", "data:text/html,hi",
            "blob:https://example.com/1", "view-source:https://example.com", "moz-extension://abc/page.html")
            .forEach { assertTrue(it, isWebAddress(it)) }
    }

    @Test fun anAddressWithoutASchemeStaysInTheBrowser() = assertTrue(isWebAddress("example.com/path?x=1"))

    @Test fun linksMeantForAppsDoNot() {
        listOf("myapp://callback?code=1", "kakaotalk://oauth", "mailto:a@example.com", "tel:0212345678", "market://details?id=a.b",
            "intent://pay#Intent;scheme=ispmobile;package=kvp.jjy.MispAndroid320;end", "com.example.app:/oauth2redirect")
            .forEach { assertFalse(it, isWebAddress(it)) }
    }

    @Test fun theDialogShowsWhereTheLinkGoesWithoutItsSecrets() {
        assertEquals("myapp://callback", externalLinkLabel("myapp://callback?code=SECRET#frag"))
        assertEquals("mailto:", externalLinkLabel("mailto:a@example.com"))
        assertEquals("com.example.app:", externalLinkLabel("com.example.app:/oauth2redirect?code=SECRET"))
    }

    @Test fun anIntentLinkIsNamedByTheAppItAsksFor() {
        assertEquals("kvp.jjy.MispAndroid320", externalLinkLabel("intent://pay?x=1#Intent;scheme=ispmobile;package=kvp.jjy.MispAndroid320;end"))
        assertEquals("ispmobile://pay", externalLinkLabel("intent://pay?x=1#Intent;scheme=ispmobile;end"))
    }
}
