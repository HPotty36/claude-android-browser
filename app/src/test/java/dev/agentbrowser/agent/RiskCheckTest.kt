package dev.agentbrowser.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskCheckTest {
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test fun riskyFlagWins() = assertTrue(needsConfirmation("click", json("""{"ref":1,"risky":true}"""), "다음"))
    @Test fun koreanKeyword() = assertTrue(needsConfirmation("click", json("""{"ref":1}"""), "결제하기"))
    @Test fun englishKeywordCase() = assertTrue(needsConfirmation("click", json("""{"ref":1}"""), "Place ORDER"))
    @Test fun plainClick() = assertFalse(needsConfirmation("click", json("""{"ref":1}"""), "다음 페이지"))
    @Test fun typeWithoutSubmit() =
        assertFalse(needsConfirmation("type", json("""{"ref":1,"text":"a","submit":false}"""), "삭제 사유"))
    @Test fun typeWithSubmit() =
        assertTrue(needsConfirmation("type", json("""{"ref":1,"text":"a","submit":true}"""), "메시지 보내기"))
    @Test fun readNeverConfirms() = assertFalse(needsConfirmation("read_page", json("{}"), null))
    @Test fun riskyClickWithUnknownLabel() = assertTrue(needsConfirmation("click", json("""{"ref":9,"risky":true}"""), null))
}
