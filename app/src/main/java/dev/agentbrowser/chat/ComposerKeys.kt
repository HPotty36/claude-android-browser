package dev.agentbrowser.chat

import android.view.KeyEvent

/**
 * Whether a key pressed in the message box sends the message. With a hardware keyboard Enter sends, as in any
 * chat; Shift+Enter or Alt+Enter starts a new line.
 */
fun sendsMessage(keyCode: Int, shift: Boolean, ctrl: Boolean, alt: Boolean): Boolean =
    (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) && !shift && !alt

/** [text] with a line break in place of the selection, and where the cursor goes after it. */
fun withNewLine(text: String, selectionStart: Int, selectionEnd: Int): Pair<String, Int> {
    val from = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val to = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    return text.replaceRange(from, to, "\n") to from + 1
}
