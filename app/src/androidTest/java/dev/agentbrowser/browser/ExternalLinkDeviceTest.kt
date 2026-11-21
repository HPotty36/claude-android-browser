package dev.agentbrowser.browser

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.agentbrowser.App
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalLinkDeviceTest {
    private val server = MockWebServer()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var controller: BrowserController

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                // What a sign-in page does once it is done: send the browser back to the app.
                request.path.orEmpty().startsWith("/login-done") ->
                    MockResponse().setResponseCode(302).setHeader("Location", "agentbrowsertest://callback?code=42")
                else -> MockResponse().setHeader("content-type", "text/html; charset=utf-8")
                    .setBody("<!doctype html><title>t</title><body><a id=a href='agentbrowsertest://tap?x=1'>open</a></body>")
            }
        }
        server.start()
        val app = instrumentation.targetContext.applicationContext as App
        instrumentation.runOnMainSync {
            controller = app.browser
            controller.dismissExternalLink()
        }
    }

    @After fun stop() {
        instrumentation.runOnMainSync { controller.dismissExternalLink() }
        server.shutdown()
    }

    @Test fun aRedirectToAnAppIsOfferedInsteadOfDropped() {
        val url = server.url("/login-done?${System.nanoTime()}").toString()
        instrumentation.runOnMainSync { controller.load(url) }
        val offered = runBlocking { withTimeout(60_000) { controller.externalLink.first { it != null } } }
        assertEquals("agentbrowsertest://callback?code=42", offered)
    }

    @Test fun dismissingClearsTheOffer() {
        val url = server.url("/login-done?${System.nanoTime()}").toString()
        instrumentation.runOnMainSync { controller.load(url) }
        runBlocking { withTimeout(60_000) { controller.externalLink.first { it != null } } }
        instrumentation.runOnMainSync { controller.dismissExternalLink() }
        assertNull(controller.externalLink.value)
    }

    @Test fun anIntentLinkCannotNameTheComponentItStarts() {
        val intent = externalIntent("intent://pay#Intent;scheme=ispmobile;package=a.b;component=a.b/.Hidden;end")
        assertNotNull(intent)
        assertNull(intent!!.component)
        assertNull(intent.selector)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals("a.b", intent.`package`)
        assertEquals("ispmobile://pay", intent.dataString)
    }

    @Test fun aPlainAppLinkBecomesAViewIntent() {
        val intent = externalIntent("agentbrowsertest://callback?code=42")!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("agentbrowsertest://callback?code=42", intent.dataString)
    }

    @Test fun theFallbackPageOfAnIntentLinkIsOnlyEverAWebPage() {
        assertEquals("https://example.com/get", externalFallback("intent://x#Intent;scheme=foo;S.browser_fallback_url=https%3A%2F%2Fexample.com%2Fget;end"))
        assertNull(externalFallback("intent://x#Intent;scheme=foo;S.browser_fallback_url=javascript%3Aalert(1);end"))
        assertNull(externalFallback("agentbrowsertest://callback"))
    }
}
