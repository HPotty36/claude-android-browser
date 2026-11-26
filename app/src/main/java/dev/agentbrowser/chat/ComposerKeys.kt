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

/**
 * When [new] is [old] with one line break typed just before [cursor], the text without that break; otherwise null.
 * A keyboard app composing Korean takes a hardware Enter itself and types a line break, so the app never sees
 * the key: this is how such an Enter is recognised. The syllable being composed ([oldComposition], in [old]) may
 * be committed in the same change; anything more changing with the break is a paste, not an Enter.
 */
fun typedLineBreak(old: String, oldComposition: IntRange?, new: String, cursor: Int): String? {
    if (cursor !in 1..new.length || new[cursor - 1] != '\n') return null
    val typed = new.removeRange(cursor - 1, cursor)
    if (typed == old) return typed
    val composing = oldComposition?.takeIf { !it.isEmpty() && it.first >= 0 && it.last < old.length } ?: return null
    val before = old.substring(0, composing.first)
    val after = old.substring(composing.last + 1)
    if (!typed.startsWith(before) || !typed.endsWith(after) || typed.length < before.length + after.length) return null
    val committed = typed.substring(before.length, typed.length - after.length)
    if ('\n' in committed || committed.length > composing.count() + 1) return null
    // The break comes right after what was being composed, where the cursor was.
    if (cursor - 1 != before.length + committed.length) return null
    return typed
}
