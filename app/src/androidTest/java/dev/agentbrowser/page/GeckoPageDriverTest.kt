package dev.agentbrowser.page

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.agentbrowser.App
import dev.agentbrowser.browser.BrowserController
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoPageDriverTest {
    private val fixture = """
        <!doctype html><html><head><meta charset="utf-8"><title>fixture</title></head><body>
        <a href="/login">로그인</a>
        <input type="text" placeholder="검색" id="q">
        <button onclick="document.title='clicked'">검색</button>
        <input type="password" aria-label="비밀번호" value="secret123">
        <button style="display:none">숨김</button>
        <p>${"가나다라마 ".repeat(8000)}</p>
        </body></html>
    """.trimIndent()

    private val server = MockWebServer()
    private lateinit var controller: BrowserController
    private lateinit var driver: PageDriver

    @Before fun openFixture() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse().setHeader("content-type", "text/html; charset=utf-8").setBody(fixture)
        }
        server.start()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as App
        instrumentation.runOnMainSync { controller = app.browser }
        driver = controller.pageDriver
        val url = server.url("/page?t=${System.nanoTime()}").toString()
        instrumentation.runOnMainSync { controller.load(url) }
        runBlocking {
            withTimeout(60_000) {
                while (driver.snapshot().url != url) delay(300)
            }
        }
    }

    @After fun stop() = server.shutdown()

    private fun refOf(text: String, kind: String, label: String): Int =
        Regex("""\[(\d+)] $kind "$label"""").find(text)!!.groupValues[1].toInt()

    @Test fun snapshotNumbersVisibleElements() = runBlocking {
        val page = driver.snapshot()
        assertEquals("fixture", page.title)
        assertTrue(page.text.contains("""link "로그인""""))
        assertTrue(page.text.contains("""textbox "검색" value="""""))
        assertTrue(page.text.contains("""button "검색""""))
        assertFalse(page.text.contains("숨김"))
    }

    @Test fun snapshotNeverContainsPasswordValue() = runBlocking {
        val page = driver.snapshot()
        assertFalse(page.text.contains("secret123"))
        assertTrue(page.text.contains("""password "비밀번호" value="""""))
        assertTrue(page.hasPasswordField)
    }

    @Test fun snapshotIsTruncated() = runBlocking {
        val text = driver.snapshot().text
        assertTrue(text.endsWith("[잘림]"))
        assertEquals(30_000 + "[잘림]".length, text.length)
    }

    @Test fun elementInfo() = runBlocking {
        val text = driver.snapshot().text
        val password = refOf(text, "password", "비밀번호")
        assertEquals("password", driver.elementKind(password))
        assertEquals("비밀번호", driver.elementLabel(password))
        assertNull(driver.elementLabel(999))
        assertNull(driver.elementKind(999))
    }

    @Test fun clickRunsHandler() = runBlocking {
        val button = refOf(driver.snapshot().text, "button", "검색")
        assertTrue(driver.click(button) is ActionResult.Ok)
        assertEquals("clicked", driver.snapshot().title)
    }

    @Test fun typeSetsValue() = runBlocking {
        val box = refOf(driver.snapshot().text, "textbox", "검색")
        assertTrue(driver.type(box, "운동화", submit = false) is ActionResult.Ok)
        assertTrue(driver.snapshot().text.contains("""textbox "검색" value="운동화""""))
    }

    @Test fun typeIntoPasswordIsRefused() = runBlocking {
        val password = refOf(driver.snapshot().text, "password", "비밀번호")
        assertTrue(driver.type(password, "x", submit = false) is ActionResult.Error)
    }

    @Test fun staleRefReturnsError() = runBlocking {
        driver.snapshot()
        val result = driver.click(999)
        assertTrue(result is ActionResult.Error)
        assertTrue((result as ActionResult.Error).message.contains("다시 읽으세요"))
    }

    @Test fun screenshotBlockedOnPasswordPage() = runBlocking {
        assertNull(driver.screenshot())
    }

    @Test fun navigateRejectsNonHttp() = runBlocking {
        assertTrue(driver.navigate("javascript:alert(1)") is ActionResult.Error)
        assertTrue(driver.navigate("file:///sdcard/x.html") is ActionResult.Error)
    }
}
