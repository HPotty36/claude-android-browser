package dev.agentbrowser.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.agentbrowser.App
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabsTest {
    private val server = MockWebServer()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var controller: BrowserController

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse()
                .setHeader("content-type", "text/html; charset=utf-8")
                .setBody("<!doctype html><title>${request.path}</title><body><p>page ${request.path}</p></body>")
        }
        server.start()
        val app = instrumentation.targetContext.applicationContext as App
        instrumentation.runOnMainSync {
            controller = app.browser
            // Start every test from one blank tab.
            controller.tabs.value.drop(1).forEach { controller.close(it.id) }
        }
    }

    @After fun stop() = server.shutdown()

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun awaitSnapshotUrl(url: String) = runBlocking {
        withTimeout(60_000) { while (controller.pageDriver.snapshot().url != url) delay(300) }
    }

    @Test fun newTabBecomesCurrent() {
        val before = controller.tabs.value.size
        onMain { controller.newTab() }
        assertEquals(before + 1, controller.tabs.value.size)
        assertEquals(controller.tabs.value.last().id, controller.current.value.id)
    }

    @Test fun closingTheCurrentTabSelectsItsNeighbour() {
        onMain { controller.newTab() }
        val first = controller.tabs.value.first()
        val second = controller.current.value
        onMain { controller.close(second.id) }
        assertEquals(first.id, controller.current.value.id)
        assertFalse(controller.tabs.value.any { it.id == second.id })
    }

    @Test fun closingTheLastTabOpensABlankOne() {
        val only = controller.current.value
        onMain { controller.close(only.id) }
        assertEquals(1, controller.tabs.value.size)
        assertTrue(controller.current.value.id != only.id)
    }

    @Test fun eachTabKeepsItsOwnPageAndTheDriverFollowsTheCurrentTab() {
        val a = server.url("/a?${System.nanoTime()}").toString()
        val b = server.url("/b?${System.nanoTime()}").toString()
        onMain { controller.load(a) }
        awaitSnapshotUrl(a)
        val first = controller.current.value
        onMain { controller.newTab(b) }
        awaitSnapshotUrl(b)
        assertEquals(b, controller.url.value)
        onMain { controller.select(first.id) }
        awaitSnapshotUrl(a)
        assertEquals(a, controller.url.value)
    }
}
