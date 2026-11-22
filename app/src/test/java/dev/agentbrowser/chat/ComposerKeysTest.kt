package dev.agentbrowser.chat

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerKeysTest {
    @Test fun enterSends() = assertTrue(sendsMessage(KeyEvent.KEYCODE_ENTER, shift = false, ctrl = false, alt = false))
    @Test fun theNumberPadEnterSendsToo() = assertTrue(sendsMessage(KeyEvent.KEYCODE_NUMPAD_ENTER, shift = false, ctrl = false, alt = false))
    @Test fun shiftEnterStartsANewLine() = assertFalse(sendsMessage(KeyEvent.KEYCODE_ENTER, shift = true, ctrl = false, alt = false))
    @Test fun altEnterStartsANewLine() = assertFalse(sendsMessage(KeyEvent.KEYCODE_ENTER, shift = false, ctrl = false, alt = true))
    @Test fun ctrlEnterSends() = assertTrue(sendsMessage(KeyEvent.KEYCODE_ENTER, shift = false, ctrl = true, alt = false))
    @Test fun otherKeysAreTyping() = assertFalse(sendsMessage(KeyEvent.KEYCODE_A, shift = false, ctrl = false, alt = false))

    @Test fun aNewLineGoesWhereTheCursorIs() = assertEquals("ab\ncd" to 3, withNewLine("abcd", 2, 2))
    @Test fun aNewLineReplacesTheSelection() = assertEquals("a\nd" to 2, withNewLine("abcd", 1, 3))
    @Test fun aBackwardsSelectionIsReplacedToo() = assertEquals("a\nd" to 2, withNewLine("abcd", 3, 1))
}
