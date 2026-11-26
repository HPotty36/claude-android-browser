package dev.agentbrowser.page

import androidx.test.core.app.ActivityScenario
import dev.agentbrowser.MainActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.agentbrowser.App
import dev.agentbrowser.browser.BrowserController
import dev.agentbrowser.translate.TextItem
import dev.agentbrowser.translate.TranslationState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The page side of translation: what is read out of a page and how the translation goes back in. */
@RunWith(AndroidJUnit4::class)
class TranslatablePageTest {
    private val server = MockWebServer()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var controller: BrowserController

    // Japanese is written without spaces, so the pieces split by the bold text and the link touch each other.
    private val page = """
        <!doctype html><html lang="ja"><head><meta charset="utf-8"><title>t</title></head><body>
        <p id="p">傘を持って出かけると<b>安心</b>でしょう。<a href="/x">詳しくはこちら</a>。</p>
        <pre>code stays</pre></body></html>
    """.trimIndent()

    // The page is shown, as when it is translated: off screen a paragraph has no width and counts as hidden.
    private lateinit var screen: ActivityScenario<MainActivity>

    @Before fun open() {
        screen = ActivityScenario.launch(MainActivity::class.java)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse().setHeader("content-type", "text/html; charset=utf-8").setBody(page)
        }
        server.start()
        val app = instrumentation.targetContext.applicationContext as App
        instrumentation.runOnMainSync { controller = app.browser }
        val url = server.url("/ja?t=${System.nanoTime()}").toString()
        instrumentation.runOnMainSync { controller.load(url) }
        runBlocking { withTimeout(60_000) { while (controller.pageDriver.snapshot().url != url) delay(300) } }
    }

    @After fun close() {
        screen.close()
        server.shutdown()
    }

    private val driver get() = controller.current.value.driver

    @Test fun punctuationOnItsOwnIsReadOutButCodeIsNot() = runBlocking {
        val texts = driver.collect().map { it.text.trim() }
        assertEquals(listOf("傘を持って出かけると", "安心", "でしょう。", "詳しくはこちら", "。"), texts)
    }

    @Test fun thePiecesOfOneParagraphAreMarkedAsOneBlock() = runBlocking {
        assertEquals(1, driver.collect().map { it.block }.distinct().size)
    }

    @Test fun aSpaceTheTranslationStartsWithIsKept() = runBlocking {
        val items = driver.collect()
        val korean = listOf("우산을 챙겨 나가시면", " 안심", "일 것입니다.", " 자세한 내용은 여기", ".")
        driver.apply(items.mapIndexed { index, item -> TextItem(item.id, korean[index]) })
        val text = controller.pageDriver.snapshot().text
        assertTrue(text, "우산을 챙겨 나가시면 안심일 것입니다. 자세한 내용은 여기." in text)
    }

    // Reloading brings the page back in its own language, so it must not still be shown as translated.
    @Test fun aReloadedPageIsNoLongerShownAsTranslated() = runBlocking {
        val tab = controller.current.value
        instrumentation.runOnMainSync { tab.setTranslation(TranslationState.Translated) }
        instrumentation.runOnMainSync { controller.reload() }
        withTimeout(20_000) { while (tab.translation.value != TranslationState.Off) delay(200) }
    }
}
