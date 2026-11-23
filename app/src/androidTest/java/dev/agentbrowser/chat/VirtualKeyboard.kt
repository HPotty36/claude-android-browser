package dev.agentbrowser.chat

import android.view.inputmethod.InputMethodManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.io.OutputStream

/**
 * A USB keyboard plugged in for the length of a test, through the platform's `uinput` tool. The system treats
 * it as real hardware: the keyboard app switches to its hardware mode and key presses carry the device's id.
 */
class VirtualKeyboard : AutoCloseable {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val pipes: Array<ParcelFileDescriptor> = instrumentation.uiAutomation.executeShellCommandRwe("uinput -")
    private val input: OutputStream = ParcelFileDescriptor.AutoCloseOutputStream(pipes[1])

    init {
        send(
            """{"id":1,"command":"register","name":"Test USB Keyboard","vid":1133,"pid":49948,"bus":"usb",""" +
                """"configuration":[{"type":"UI_SET_EVBIT","data":["EV_KEY","EV_SYN"]},""" +
                """{"type":"UI_SET_KEYBIT","data":[${KEYS.joinToString(",") { "\"$it\"" }}]}]}""",
        )
        val end = SystemClock.uptimeMillis() + 15_000
        while (!plugged()) {
            if (SystemClock.uptimeMillis() > end) {
                input.close()
                val errors = ParcelFileDescriptor.AutoCloseInputStream(pipes[2]).bufferedReader().use { it.readText() }
                error("The system never saw the keyboard. uinput said: $errors")
            }
            Thread.sleep(200)
        }
        Thread.sleep(1_000)
    }

    /**
     * Presses [key] (a Linux key name such as KEY_A) while holding [modifiers]. The modifiers go down a moment
     * before the key, as fingers do: pressed in the same instant, a Shift can reach the app after the keyboard
     * app has already acted on the Enter.
     */
    fun press(key: String, vararg modifiers: String) {
        if (modifiers.isNotEmpty()) inject(modifiers.flatMap { down(it) }).also { Thread.sleep(60) }
        inject(down(key) + up(key))
        if (modifiers.isNotEmpty()) Thread.sleep(60).also { inject(modifiers.reversed().flatMap { up(it) }) }
        Thread.sleep(150)
    }

    private fun inject(events: List<String>) = send("""{"id":1,"command":"inject","events":[${events.joinToString(",")}]}""")

    /** The language the keyboard app types in, such as "ko" or "en". */
    fun language(): String {
        val imm = instrumentation.targetContext.getSystemService(InputMethodManager::class.java)
        return imm.currentInputMethodSubtype?.languageTag?.substringBefore('-').orEmpty()
    }

    /** Moves the keyboard app to [language] with Ctrl+Space; false when it has no such language. */
    fun switchTo(language: String): Boolean {
        repeat(4) {
            if (language() == language) return true
            press("KEY_SPACE", CTRL)
            Thread.sleep(800)
        }
        return language() == language
    }

    /** The keyboard app's current language as the system records it, so a test can put it back. */
    fun subtype(): String = shell("settings get secure selected_input_method_subtype")

    /** Whether the system's configuration says a typing keyboard is plugged in. */
    private fun plugged() = shell("am get-config").lineSequence().any { it.startsWith("config:") && "-qwerty-" in it }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }

    /** Presses Ctrl+Space until the keyboard app is back on [subtype]. */
    fun restore(subtype: String) {
        repeat(4) {
            if (subtype() == subtype) return
            press("KEY_SPACE", CTRL)
            Thread.sleep(800)
        }
    }

    /** Types lower-case letters. */
    fun type(letters: String) = letters.forEach { press("KEY_${it.uppercaseChar()}") }

    private fun down(key: String) = listOf("\"EV_KEY\"", "\"$key\"", "1", "\"EV_SYN\"", "\"SYN_REPORT\"", "0")
    private fun up(key: String) = listOf("\"EV_KEY\"", "\"$key\"", "0", "\"EV_SYN\"", "\"SYN_REPORT\"", "0")

    private fun send(json: String) {
        input.write((json + "\n").toByteArray())
        input.flush()
    }

    /** Unplugs the keyboard. */
    override fun close() {
        input.close()
        pipes[0].close()
        pipes[2].close()
        val end = SystemClock.uptimeMillis() + 10_000
        while (plugged() && SystemClock.uptimeMillis() < end) Thread.sleep(200)
    }

    companion object {
        const val SHIFT = "KEY_LEFTSHIFT"
        const val CTRL = "KEY_LEFTCTRL"
        private val KEYS = ('A'..'Z').map { "KEY_$it" } +
            listOf("KEY_ENTER", "KEY_SPACE", "KEY_TAB", "KEY_ESC", "KEY_BACKSPACE", SHIFT, CTRL)
    }
}
