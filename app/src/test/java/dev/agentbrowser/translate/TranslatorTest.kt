package dev.agentbrowser.translate

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatorTest {
    private class FakePage(val items: List<TextItem>) : TranslatablePage {
        val applied = mutableMapOf<Int, String>()
        override suspend fun collect(): List<TextItem> = items
        override suspend fun apply(items: List<TextItem>) { items.forEach { applied[it.id] = it.text } }
        override suspend fun restore() = applied.clear()
    }

    private class FakeClient(val fail: (List<String>) -> Boolean = { false }) : TranslationClient {
        val calls = mutableListOf<Pair<List<String>, TranslateTarget>>()
        override suspend fun translate(texts: List<String>, target: TranslateTarget): List<String> {
            synchronized(calls) { calls += texts to target }
            if (fail(texts)) throw IllegalStateException("no")
            return texts.map { "<$it>" }
        }
    }

    private fun items(vararg texts: String) = texts.mapIndexed { index, text -> TextItem(index + 1, text) }

    @Test fun piecesAreGroupedUpToTheLimit() {
        val chunks = chunkItems(items("aaaa", "bbbb", "cc", "dddddd"), maxChars = 8)
        assertEquals(listOf(listOf(1, 2), listOf(3, 4)), chunks.map { chunk -> chunk.map { it.id } })
    }

    @Test fun aPieceLongerThanTheLimitGoesAlone() {
        val chunks = chunkItems(items("aa", "x".repeat(50), "bb"), maxChars = 8)
        assertEquals(listOf(listOf(1), listOf(2), listOf(3)), chunks.map { chunk -> chunk.map { it.id } })
    }

    @Test fun noPiecesMeansNoChunks() = assertTrue(chunkItems(emptyList(), maxChars = 8).isEmpty())

    @Test fun aPlainArrayIsRead() = assertEquals(listOf("가", "나"), parseTranslations("""["가","나"]""", expected = 2))
    @Test fun anArrayInsideAFenceOrASentenceIsRead() =
        assertEquals(listOf("가", "나 [1]"), parseTranslations("Here you go:\n```json\n[\"가\", \"나 [1]\"]\n```", expected = 2))
    @Test fun aReplyOfTheWrongLengthIsRejected() = assertNull(parseTranslations("""["가"]""", expected = 2))
    @Test fun aReplyThatIsNotAListOfTextIsRejected() {
        assertNull(parseTranslations("""{"a":"가"}""", expected = 1))
        assertNull(parseTranslations("""[1, 2]""", expected = 2))
        assertNull(parseTranslations("sorry", expected = 1))
    }

    @Test fun everyPieceIsTranslatedAndPutBack() = runBlocking {
        val page = FakePage(items("one", "two", "three"))
        val client = FakeClient()
        val result = Translator(client, maxChunkChars = 6).translate(page, TranslateTarget.KOREAN)
        assertEquals(mapOf(1 to "<one>", 2 to "<two>", 3 to "<three>"), page.applied)
        assertEquals(TranslationResult(translated = 3, total = 3), result)
        assertTrue(client.calls.all { it.second == TranslateTarget.KOREAN })
    }

    @Test fun aChunkThatFailsLeavesItsPiecesAsTheyWere() = runBlocking {
        val page = FakePage(items("one", "two", "three"))
        val result = Translator(FakeClient(fail = { "two" in it }), maxChunkChars = 3).translate(page, TranslateTarget.ENGLISH)
        assertEquals(mapOf(1 to "<one>", 3 to "<three>"), page.applied)
        assertEquals(TranslationResult(translated = 2, total = 3), result)
    }

    @Test fun progressIsReportedAsChunksFinish() = runBlocking {
        val seen = mutableListOf<Pair<Int, Int>>()
        Translator(FakeClient(), maxChunkChars = 3, parallel = 1)
            .translate(FakePage(items("one", "two", "three")), TranslateTarget.KOREAN) { done, total -> seen += done to total }
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), seen)
    }

    @Test fun aPageWithNothingToTranslateAsksNothing() = runBlocking {
        val client = FakeClient()
        val result = Translator(client).translate(FakePage(emptyList()), TranslateTarget.KOREAN)
        assertEquals(TranslationResult(0, 0), result)
        assertTrue(client.calls.isEmpty())
    }

    @Test fun theModelIsToldTheTargetAndGivenOnlyTheTexts() = runBlocking {
        val sent = mutableListOf<Pair<String, String>>()
        val client = ClaudeTranslationClient { system, user ->
            sent += system to user
            """["하나","둘"]"""
        }
        assertEquals(listOf("하나", "둘"), client.translate(listOf("one", "two \"q\""), TranslateTarget.KOREAN))
        assertTrue(sent.single().first.contains("Korean"))
        assertEquals("""["one","two \"q\""]""", sent.single().second)
    }

    @Test(expected = IllegalStateException::class)
    fun aReplyThatCannotBeUsedIsAnError(): Unit = runBlocking {
        ClaudeTranslationClient { _, _ -> "I cannot do that" }.translate(listOf("one"), TranslateTarget.KOREAN)
    }

    // A sentence cut by bold text or a link goes to the model whole, its pieces marked, and comes back split.
    @Test fun piecesAreMarkedInOrder() = assertEquals("⟦1⟧a⟦2⟧b ⟦3⟧c", joinPieces(listOf("a", "b ", "c")))

    @Test fun theSpacesTheTranslationPutsBetweenPiecesAreKept() = assertEquals(
        listOf("우산을 챙겨 나가시면 ", "안심", "일 것입니다. ", "자세한 내용은 여기", "."),
        splitPieces("⟦1⟧우산을 챙겨 나가시면 ⟦2⟧안심⟦3⟧일 것입니다. ⟦4⟧자세한 내용은 여기⟦5⟧.", count = 5),
    )

    @Test fun aReplyThatLostOrMovedAMarkerIsNotSplit() {
        assertNull(splitPieces("⟦1⟧a⟦3⟧c", count = 3))
        assertNull(splitPieces("⟦2⟧b⟦1⟧a", count = 2))
        assertNull(splitPieces("⟦1⟧a⟦2⟧b⟦2⟧b", count = 2))
        assertNull(splitPieces("a b", count = 2))
    }

    @Test fun piecesNextToEachOtherInOneBlockAreGrouped() {
        val groups = groupByBlock(listOf(TextItem(1, "a", block = 7), TextItem(2, "b", block = 7), TextItem(3, "c", block = 8), TextItem(4, "d", block = 7)))
        assertEquals(listOf(listOf(1, 2), listOf(3), listOf(4)), groups.map { group -> group.map { it.id } })
    }

    private class UpperClient : TranslationClient {
        val calls = mutableListOf<List<String>>()
        var breakMarkers = false
        override suspend fun translate(texts: List<String>, target: TranslateTarget): List<String> {
            calls += texts
            return texts.map { text -> if (breakMarkers && '⟦' in text) text.replace("⟦", "[").uppercase() else text.uppercase() }
        }
    }

    @Test fun aBlockGoesAsOneStringAndComesBackInItsPieces() = runBlocking {
        val page = FakePage(listOf(TextItem(1, "one ", block = 5), TextItem(2, "two", block = 5), TextItem(3, "three", block = 6)))
        val client = UpperClient()
        val result = Translator(client).translate(page, TranslateTarget.KOREAN)
        assertEquals(listOf(listOf("⟦1⟧one ⟦2⟧two", "three")), client.calls)
        assertEquals(mapOf(1 to "ONE ", 2 to "TWO", 3 to "THREE"), page.applied)
        assertEquals(TranslationResult(translated = 3, total = 3), result)
    }

    @Test fun aBlockWhoseMarkersCameBackBrokenIsAskedAgainPieceByPiece() = runBlocking {
        val page = FakePage(listOf(TextItem(1, "one", block = 5), TextItem(2, "two", block = 5)))
        val client = UpperClient().apply { breakMarkers = true }
        val result = Translator(client).translate(page, TranslateTarget.KOREAN)
        assertEquals(listOf(listOf("⟦1⟧one⟦2⟧two"), listOf("one", "two")), client.calls)
        assertEquals(mapOf(1 to "ONE", 2 to "TWO"), page.applied)
        assertEquals(TranslationResult(translated = 2, total = 2), result)
    }
}
