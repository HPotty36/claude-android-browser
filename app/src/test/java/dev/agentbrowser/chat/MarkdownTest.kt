package dev.agentbrowser.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {
    @Test fun plainTextIsOneSegment() = assertEquals(listOf(Segment("안녕하세요", false)), boldSegments("안녕하세요"))

    @Test fun boldMarkersAreRemoved() = assertEquals(
        listOf(Segment("1. ", false), Segment("상품:", true), Segment(" 운동화", false)),
        boldSegments("1. **상품:** 운동화"),
    )

    @Test fun twoBoldRuns() = assertEquals(
        listOf(Segment("a", true), Segment(" b ", false), Segment("c", true)),
        boldSegments("**a** b **c**"),
    )

    @Test fun unclosedMarkerStaysLiteral() =
        assertEquals(listOf(Segment("가격 ** 미정", false)), boldSegments("가격 ** 미정"))

    @Test fun emptyTextHasNoSegments() = assertEquals(emptyList<Segment>(), boldSegments(""))
}
