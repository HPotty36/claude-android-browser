package dev.agentbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabOrderTest {
    @Test fun closingAnotherTabKeepsTheCurrentOne() = assertEquals(2L, currentAfterClose(listOf(1, 2, 3), current = 2, closing = 3))
    @Test fun closingTheCurrentTabMovesToTheOneBefore() = assertEquals(2L, currentAfterClose(listOf(1, 2, 3), current = 3, closing = 3))
    @Test fun closingTheFirstTabMovesToTheNext() = assertEquals(2L, currentAfterClose(listOf(1, 2, 3), current = 1, closing = 1))
    @Test fun closingTheOnlyTabLeavesNone() = assertNull(currentAfterClose(listOf(7), current = 7, closing = 7))
    @Test fun closingAnUnknownTabChangesNothing() = assertEquals(1L, currentAfterClose(listOf(1, 2), current = 1, closing = 9))
}
