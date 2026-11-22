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
}
