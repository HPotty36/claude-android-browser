package dev.agentbrowser.browser

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.agentbrowser.App
import dev.agentbrowser.MainActivity
import dev.agentbrowser.chat.VirtualKeyboard
import dev.agentbrowser.chat.VirtualKeyboard.Companion.CTRL
import java.net.URLDecoder
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Typing an address or a search from a keyboard plugged into the phone. Needs the network to reach the search engine. */
@RunWith(AndroidJUnit4::class)
class AddressKeyboardTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as App

    private fun waitUntil(what: String, timeoutMs: Long = 30_000, check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (!check()) {
            if (SystemClock.uptimeMillis() > end) fail("Timed out waiting until $what")
            Thread.sleep(200)
        }
    }

    private fun currentUrl() = URLDecoder.decode(app.browser.current.value.url.value, "UTF-8")

    // While it composes Korean, the keyboard app takes Enter itself and types a line break into the field.
    @Test fun enterSearchesForWhatWasComposedInKorean() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitUntil("the browser listens for shortcuts") { app.shortcuts.subscriptionCount.value > 0 }
            VirtualKeyboard().use { keyboard ->
                keyboard.press("KEY_L", CTRL)
                Thread.sleep(800)
                val start = keyboard.subtype()
                try {
                    assumeTrue("The keyboard app has Korean", keyboard.switchTo("ko"))
                    keyboard.type("spdlqj")
                    keyboard.press("KEY_ENTER")
                    waitUntil("the search for 네이버 opens, not ${currentUrl()}") { "네이버" in currentUrl() }
                } finally {
                    // The keyboard app changes language only while a field has the cursor.
                    keyboard.press("KEY_L", CTRL)
                    Thread.sleep(800)
                    keyboard.restore(start)
                    keyboard.press("KEY_ESC")
                    // The search page is a real one, still loading; it is no business of the tests after this.
                    app.browser.settle(instrumentation)
                }
            }
        }
    }
}
