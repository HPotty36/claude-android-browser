package dev.agentbrowser.browser

import android.view.KeyEvent

/** What a hardware keyboard can ask of the browser. */
enum class KeyShortcut { FocusAddress, NewTab, CloseTab, NextTab, PreviousTab, Reload, Back, Forward, ToggleBookmark, ToggleClaude }

/** The shortcut a key press means, or null when the key belongs to the page. They follow desktop browsers. */
fun shortcutFor(keyCode: Int, ctrl: Boolean, shift: Boolean, alt: Boolean): KeyShortcut? = when {
    ctrl && !shift && !alt -> when (keyCode) {
        KeyEvent.KEYCODE_L -> KeyShortcut.FocusAddress
        KeyEvent.KEYCODE_T -> KeyShortcut.NewTab
        KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_F4 -> KeyShortcut.CloseTab
        KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_PAGE_DOWN -> KeyShortcut.NextTab
        KeyEvent.KEYCODE_PAGE_UP -> KeyShortcut.PreviousTab
        KeyEvent.KEYCODE_R -> KeyShortcut.Reload
        KeyEvent.KEYCODE_D -> KeyShortcut.ToggleBookmark
        KeyEvent.KEYCODE_J -> KeyShortcut.ToggleClaude
        else -> null
    }
    ctrl && shift && !alt -> if (keyCode == KeyEvent.KEYCODE_TAB) KeyShortcut.PreviousTab else null
    alt && !ctrl && !shift -> when (keyCode) {
        KeyEvent.KEYCODE_D -> KeyShortcut.FocusAddress
        KeyEvent.KEYCODE_DPAD_LEFT -> KeyShortcut.Back
        KeyEvent.KEYCODE_DPAD_RIGHT -> KeyShortcut.Forward
        else -> null
    }
    !ctrl && !shift && !alt -> when (keyCode) {
        KeyEvent.KEYCODE_F5 -> KeyShortcut.Reload
        KeyEvent.KEYCODE_F6 -> KeyShortcut.FocusAddress
        else -> null
    }
    else -> null
}
