package dev.agentbrowser.chat

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // A keyboard app composing Korean takes a hardware Enter itself and types a line break instead.
    @Test fun aLineBreakTypedAtTheEndIsAnEnter() = assertEquals("안녕", typedLineBreak("안녕", "안녕\n", cursor = 3))
    @Test fun theLastSyllableCommittedWithTheBreakIsKept() = assertEquals("안녕", typedLineBreak("안녀", "안녕\n", cursor = 3))
    @Test fun aLineBreakInTheMiddleIsAnEnterToo() = assertEquals("ab", typedLineBreak("ab", "a\nb", cursor = 2))
    @Test fun earlierLineBreaksStay() = assertEquals("a\nb", typedLineBreak("a\nb", "a\nb\n", cursor = 4))
    @Test fun typingALetterIsNotAnEnter() = assertNull(typedLineBreak("ab", "abc", cursor = 3))
    @Test fun typingAfterALineBreakIsNotAnEnter() = assertNull(typedLineBreak("a\n", "a\nb", cursor = 3))
    @Test fun deletingALineBreakIsNotAnEnter() = assertNull(typedLineBreak("a\nb", "ab", cursor = 1))
    @Test fun pastingTwoLinesIsNotAnEnter() = assertNull(typedLineBreak("", "x\ny", cursor = 3))
}
