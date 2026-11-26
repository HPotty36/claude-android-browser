package dev.agentbrowser.chat

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.agentbrowser.App
import dev.agentbrowser.MainActivity
import dev.agentbrowser.chat.VirtualKeyboard.Companion.CTRL
import dev.agentbrowser.chat.VirtualKeyboard.Companion.SHIFT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Typing to Claude from a keyboard plugged into the phone, and from the on-screen one. */
@RunWith(AndroidJUnit4::class)
class ChatKeyboardTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as App

    private fun waitUntil(what: String, timeoutMs: Long = 10_000, check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (!check()) {
            if (SystemClock.uptimeMillis() > end) fail("Timed out waiting until $what")
            Thread.sleep(100)
        }
    }

    private fun sent() = app.chat.items.value.filterIsInstance<ChatItem.User>().map { it.text }

    private fun withBrowser(block: () -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitUntil("the browser listens for shortcuts") { app.shortcuts.subscriptionCount.value > 0 }
            instrumentation.runOnMainSync { app.chat.newChat() }
            block()
        }
    }

    @Test fun enterSendsAndShiftEnterBreaksTheLine() = withBrowser {
        VirtualKeyboard().use { keyboard ->
            keyboard.press("KEY_J", CTRL)
            Thread.sleep(800)
            val start = keyboard.subtype()
            try {
                repeat(4) { if (keyboard.language() == "ko") keyboard.press("KEY_SPACE", CTRL).also { Thread.sleep(800) } }
                assumeTrue("The keyboard app types English", keyboard.language() != "ko")
                keyboard.type("hi")
                keyboard.press("KEY_ENTER", SHIFT)
                keyboard.type("yo")
                keyboard.press("KEY_ENTER")
                waitUntil("the message is sent") { sent().isNotEmpty() }
                assertEquals(listOf("hi\nyo"), sent())
            } finally {
                keyboard.restore(start)
            }
        }
    }

    // While it composes Korean, the keyboard app takes Enter itself and types a line break.
    @Test fun enterSendsWhatWasComposedInKorean() = withBrowser {
        VirtualKeyboard().use { keyboard ->
            keyboard.press("KEY_J", CTRL)
            Thread.sleep(800)
            val start = keyboard.subtype()
            try {
                assumeTrue("The keyboard app has Korean", keyboard.switchTo("ko"))
                keyboard.type("dkssud")
                keyboard.press("KEY_ENTER", SHIFT)
                keyboard.type("tptkd")
                keyboard.press("KEY_ENTER")
                waitUntil("the message is sent") { sent().isNotEmpty() }
                assertEquals(listOf("안녕\n세상"), sent())
            } finally {
                keyboard.restore(start)
            }
        }
    }

    @Test fun theOnScreenKeyboardsEnterDoesNotSend() = withBrowser {
        instrumentation.sendKeySync(
            KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_J, 0, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON),
        )
        Thread.sleep(800)
        instrumentation.sendStringSync("hi")
        val now = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            instrumentation.sendKeySync(
                KeyEvent(now, now, action, KeyEvent.KEYCODE_ENTER, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD),
            )
        }
        Thread.sleep(1_000)
        assertTrue("An on-screen Enter sent ${sent()}", sent().isEmpty())
    }

    // Some phones keep the on-screen keyboard up beside a plugged-in one. A line break that no plugged-in Enter
    // typed (here, keys sent as the on-screen keyboard's) stays a line break, as it does everywhere else.
    @Test fun aLineBreakFromTheOnScreenKeyboardBesideAPluggedInOneBreaksTheLine() = withBrowser {
        VirtualKeyboard().use { keyboard ->
            keyboard.press("KEY_J", CTRL)
            Thread.sleep(1_500)
            instrumentation.sendStringSync("hi\n")
            Thread.sleep(1_000)
            assertTrue("The on-screen Enter sent ${sent()}", sent().isEmpty())
        }
    }
}
