package dev.agentbrowser.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.agentbrowser.App
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesktopSiteDeviceTest {
    private val server = MockWebServer()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val userAgents = ConcurrentHashMap<String, String>()
    /** Every host name each path was asked for under, in order. */
    private val hosts = ConcurrentHashMap<String, MutableList<String>>()
    private lateinit var controller: BrowserController

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                userAgents[request.path.orEmpty()] = request.getHeader("User-Agent").orEmpty()
                hosts.getOrPut(request.path.orEmpty()) { java.util.concurrent.CopyOnWriteArrayList() }
                    .add(request.getHeader("Host").orEmpty().substringBefore(':'))
                return MockResponse()
                    .setHeader("content-type", "text/html; charset=utf-8")
                    .setBody("<!doctype html><title>t</title><body><p>page</p></body>")
            }
        }
        server.start()
        val app = instrumentation.targetContext.applicationContext as App
        instrumentation.runOnMainSync { controller = app.browser }
    }

    @After fun stop() {
        instrumentation.runOnMainSync { controller.setDesktopSite(false) }
        server.shutdown()
    }

    /** Loads a fresh page and returns the User-Agent the server was sent for it. */
    private fun userAgentFor(name: String): String {
        val path = "/$name?${System.nanoTime()}"
        val url = server.url(path).toString()
        instrumentation.runOnMainSync { controller.load(url) }
        return runBlocking {
            withTimeout(60_000) {
                while (userAgents[path] == null) delay(200)
                userAgents.getValue(path)
            }
        }
    }

    @Test fun theSiteIsAskedForItsDesktopVersionOnlyWhileTheModeIsOn() {
        instrumentation.runOnMainSync { controller.setDesktopSite(false) }
        assertTrue(userAgentFor("mobile").contains("Mobile"))
        instrumentation.runOnMainSync { controller.setDesktopSite(true) }
        assertFalse(userAgentFor("desktop").contains("Mobile"))
        instrumentation.runOnMainSync { controller.setDesktopSite(false) }
        assertTrue(userAgentFor("mobile-again").contains("Mobile"))
    }

    @Test fun aTabOpenedWhileTheModeIsOnAsksForTheDesktopVersionToo() {
        instrumentation.runOnMainSync { controller.setDesktopSite(true) }
        val path = "/new-tab?${System.nanoTime()}"
        val url = server.url(path).toString()
        instrumentation.runOnMainSync { controller.newTab(url) }
        val agent = runBlocking { withTimeout(60_000) { while (userAgents[path] == null) delay(200); userAgents.getValue(path) } }
        assertFalse(agent.contains("Mobile"))
        instrumentation.runOnMainSync { controller.close(controller.current.value.id) }
    }

    private fun awaitHost(path: String, host: String) = runBlocking {
        withTimeout(60_000) { while (hosts[path]?.contains(host) != true) delay(200) }
    }

    @Test fun aMobileAddressOpensAsItsDesktopOneWhileTheModeIsOn() {
        instrumentation.runOnMainSync { controller.setDesktopSite(true) }
        val path = "/opened?${System.nanoTime()}"
        val url = "http://m.test.localhost:${server.port}$path"
        instrumentation.runOnMainSync { controller.load(url) }
        awaitHost(path, "test.localhost")
        assertFalse(hosts.getValue(path).contains("m.test.localhost"))
    }

    @Test fun aMobilePageAlreadyOpenMovesToItsDesktopAddressWhenTheModeTurnsOn() {
        instrumentation.runOnMainSync { controller.setDesktopSite(false) }
        val path = "/already-open?${System.nanoTime()}"
        val url = "http://m.test.localhost:${server.port}$path"
        instrumentation.runOnMainSync { controller.load(url) }
        awaitHost(path, "m.test.localhost")
        runBlocking { withTimeout(60_000) { while (!controller.url.value.contains("m.test.localhost")) delay(200) } }
        instrumentation.runOnMainSync { controller.setDesktopSite(true) }
        awaitHost(path, "test.localhost")
    }

    @Test fun mobileAddressesAreLeftAloneWhileTheModeIsOff() {
        instrumentation.runOnMainSync { controller.setDesktopSite(false) }
        val path = "/stays-mobile?${System.nanoTime()}"
        val url = "http://m.test.localhost:${server.port}$path"
        instrumentation.runOnMainSync { controller.load(url) }
        awaitHost(path, "m.test.localhost")
        assertFalse(hosts.getValue(path).contains("test.localhost"))
    }
}
