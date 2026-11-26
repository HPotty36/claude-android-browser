package dev.agentbrowser.page

import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.agentbrowser.App
import dev.agentbrowser.MainActivity
import dev.agentbrowser.browser.BrowserController
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Reading a large, deeply nested page for translation stays well inside the time the app waits for the page. */
@RunWith(AndroidJUnit4::class)
class CollectSpeedTest {
    private val server = MockWebServer()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var controller: BrowserController
    private lateinit var screen: ActivityScenario<MainActivity>

    private val page = buildString {
        append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>big</title></head><body>")
        repeat(1_500) { n ->
            append("<div><div><div><div><div><div><div><div><div><div><div><div><section>")
            append("<p>Paragraph $n has <b>bold words</b> and <span>a <i>nested</i> run</span> and <a href=\"#$n\">a link</a>.</p>")
            append("</section></div></div></div></div></div></div></div></div></div></div></div></div>")
        }
        append("</body></html>")
    }

    @Before fun open() {
        screen = ActivityScenario.launch(MainActivity::class.java)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse().setHeader("content-type", "text/html; charset=utf-8").setBody(page)
        }
        server.start()
        val app = instrumentation.targetContext.applicationContext as App
        instrumentation.runOnMainSync { controller = app.browser }
        val url = server.url("/big?t=${System.nanoTime()}").toString()
        instrumentation.runOnMainSync { controller.load(url) }
        runBlocking { withTimeout(60_000) { while (controller.pageDriver.snapshot().url != url) delay(300) } }
    }

    @After fun close() {
        screen.close()
        server.shutdown()
    }

    @Test fun aLargePageIsReadQuickly() = runBlocking {
        val start = SystemClock.elapsedRealtime()
        val items = controller.current.value.driver.collect()
        val took = SystemClock.elapsedRealtime() - start
        Log.i("CollectSpeedTest", "collected ${items.size} pieces in $took ms")
        assertTrue("Nothing was read", items.isNotEmpty())
        assertTrue("Reading took $took ms", took < 3_000)
    }
}
