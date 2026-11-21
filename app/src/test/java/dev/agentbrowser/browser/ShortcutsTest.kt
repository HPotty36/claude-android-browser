package dev.agentbrowser.browser

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutsTest {
    private fun key(code: Int, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false) =
        shortcutFor(code, ctrl = ctrl, shift = shift, alt = alt)

    @Test fun addressBar() {
        assertEquals(KeyShortcut.FocusAddress, key(KeyEvent.KEYCODE_L, ctrl = true))
        assertEquals(KeyShortcut.FocusAddress, key(KeyEvent.KEYCODE_D, alt = true))
        assertEquals(KeyShortcut.FocusAddress, key(KeyEvent.KEYCODE_F6))
    }

    @Test fun tabs() {
        assertEquals(KeyShortcut.NewTab, key(KeyEvent.KEYCODE_T, ctrl = true))
        assertEquals(KeyShortcut.CloseTab, key(KeyEvent.KEYCODE_W, ctrl = true))
        assertEquals(KeyShortcut.NextTab, key(KeyEvent.KEYCODE_TAB, ctrl = true))
        assertEquals(KeyShortcut.PreviousTab, key(KeyEvent.KEYCODE_TAB, ctrl = true, shift = true))
        assertEquals(KeyShortcut.NextTab, key(KeyEvent.KEYCODE_PAGE_DOWN, ctrl = true))
        assertEquals(KeyShortcut.PreviousTab, key(KeyEvent.KEYCODE_PAGE_UP, ctrl = true))
    }

    @Test fun pageNavigation() {
        assertEquals(KeyShortcut.Reload, key(KeyEvent.KEYCODE_R, ctrl = true))
        assertEquals(KeyShortcut.Reload, key(KeyEvent.KEYCODE_F5))
        assertEquals(KeyShortcut.Back, key(KeyEvent.KEYCODE_DPAD_LEFT, alt = true))
        assertEquals(KeyShortcut.Forward, key(KeyEvent.KEYCODE_DPAD_RIGHT, alt = true))
    }

    @Test fun bookmarkAndClaude() {
        assertEquals(KeyShortcut.ToggleBookmark, key(KeyEvent.KEYCODE_D, ctrl = true))
        assertEquals(KeyShortcut.ToggleClaude, key(KeyEvent.KEYCODE_J, ctrl = true))
    }

    @Test fun plainTypingIsLeftToThePage() {
        assertNull(key(KeyEvent.KEYCODE_L))
        assertNull(key(KeyEvent.KEYCODE_T))
        assertNull(key(KeyEvent.KEYCODE_TAB))
        assertNull(key(KeyEvent.KEYCODE_DPAD_LEFT))
    }

    @Test fun combinationsThePageMayUseAreLeftAlone() {
        // Ctrl+Shift+T, Ctrl+Alt+L and the like are not ours.
        assertNull(key(KeyEvent.KEYCODE_T, ctrl = true, shift = true))
        assertNull(key(KeyEvent.KEYCODE_L, ctrl = true, alt = true))
        assertNull(key(KeyEvent.KEYCODE_C, ctrl = true))
        assertNull(key(KeyEvent.KEYCODE_V, ctrl = true))
    }
}
