package dev.agentbrowser.agent

import dev.agentbrowser.page.ActionResult
import dev.agentbrowser.page.PageDriver
import dev.agentbrowser.page.PageSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentLoopTest {
    private val driver = FakePageDriver()

    private fun TestScope.collect(loop: AgentLoop): List<AgentEvent> {
        val events = mutableListOf<AgentEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { loop.events.toList(events) }
        return events
    }

    private fun lastResult(messages: List<Message>) = messages.last().content.filterIsInstance<Block.ToolResult>().single()

    private fun rolesAlternate(messages: List<Message>) =
        messages.first().role == "user" && messages.zipWithNext().all { (a, b) -> a.role != b.role }

    private fun everyToolUseAnswered(messages: List<Message>): Boolean = messages.withIndex().all { (i, m) ->
        val ids = m.content.filterIsInstance<Block.ToolUse>().map { it.id }
        ids.isEmpty() || messages.getOrNull(i + 1)?.content
            ?.filterIsInstance<Block.ToolResult>()?.map { it.toolUseId }?.containsAll(ids) == true
    }

    @Test fun textOnlyReplyEmitsDone() = runTest(UnconfinedTestDispatcher()) {
        val loop = loop(FakeClaudeClient({ text("안녕하세요") }), driver)
        val events = collect(loop)
        loop.run("안녕")
        assertEquals(listOf(AgentEvent.AssistantText("안녕하세요"), AgentEvent.Done), events)
    }

    @Test fun toolUseIsExecutedAndResultSentBack() = runTest(UnconfinedTestDispatcher()) {
        val client = FakeClaudeClient({ toolUse("tu_1", "read_page", "{}") }, { text("끝") })
        val loop = loop(client, driver)
        val events = collect(loop)
        loop.run("읽어줘")
        assertEquals(1, driver.snapshots)
        val result = lastResult(client.calls[1])
        assertEquals("tu_1", result.toolUseId)
        assertFalse(result.isError)
        val sent = (result.content.single() as Block.Text).text
        assertTrue(sent.startsWith("URL: https://ex.com/\nTitle: Ex\n"))
        assertTrue(sent.contains("[1] button \"검색\""))
        assertTrue(events.any { it is AgentEvent.Action })
        assertEquals(AgentEvent.Done, events.last())
    }

    @Test fun riskyClickWaitsForConfirmAndRuns() = runTest(UnconfinedTestDispatcher()) {
        driver.labels[1] = "결제하기"
        val client = FakeClaudeClient({ toolUse("tu_1", "click", """{"ref":1}""") }, { text("끝") })
        val loop = loop(client, driver)
        val events = collect(loop)
        val job = launch { loop.run("결제해줘") }
        assertTrue(events.last() is AgentEvent.ConfirmRequest)
        assertEquals(emptyList<Int>(), driver.clicks)
        loop.confirm(true)
        job.join()
        assertEquals(listOf(1), driver.clicks)
        assertFalse(lastResult(client.calls[1]).isError)
    }

    @Test fun deniedClickIsNotExecuted() = runTest(UnconfinedTestDispatcher()) {
        driver.labels[1] = "삭제"
        val client = FakeClaudeClient({ toolUse("tu_1", "click", """{"ref":1}""") }, { text("알겠어요") })
        val loop = loop(client, driver)
        collect(loop)
        val job = launch { loop.run("지워줘") }
        loop.confirm(false)
        job.join()
        assertEquals(emptyList<Int>(), driver.clicks)
        val result = lastResult(client.calls[1])
        assertTrue(result.isError)
        assertTrue((result.content.single() as Block.Text).text.contains("declined"))
    }

    @Test fun staleRefErrorIsReturnedNotThrown() = runTest(UnconfinedTestDispatcher()) {
        driver.clickResult = ActionResult.Error("요소 [7]을 찾을 수 없습니다. 페이지를 다시 읽으세요.")
        val client = FakeClaudeClient({ toolUse("tu_1", "click", """{"ref":7}""") }, { text("다시 읽을게요") })
        val loop = loop(client, driver)
        val events = collect(loop)
        loop.run("눌러줘")
        val result = lastResult(client.calls[1])
        assertTrue(result.isError)
        assertTrue((result.content.single() as Block.Text).text.contains("다시 읽으세요"))
        assertEquals(AgentEvent.Done, events.last())
    }

    @Test fun typingIntoPasswordFieldIsRefused() = runTest(UnconfinedTestDispatcher()) {
        driver.kinds[4] = "password"
        driver.labels[4] = "비밀번호"
        val client = FakeClaudeClient(
            { toolUse("tu_1", "type", """{"ref":4,"text":"hunter2","submit":false}""") }, { text("직접 입력해 주세요") },
        )
        val loop = loop(client, driver)
        collect(loop)
        loop.run("로그인해줘")
        assertEquals(emptyList<String>(), driver.typed)
        val result = lastResult(client.calls[1])
        assertTrue(result.isError)
        assertTrue((result.content.single() as Block.Text).text.contains("themselves"))
    }

    @Test fun stopsAtStepLimit() = runTest(UnconfinedTestDispatcher()) {
        var n = 0
        val client = FakeClaudeClient().apply { fallback = { toolUse("tu_${n++}", "read_page", "{}") } }
        val loop = loop(client, driver, maxSteps = 3)
        val events = collect(loop)
        loop.run("계속 읽어")
        assertEquals(3, driver.snapshots)
        assertEquals(AgentEvent.StepLimit, events.last())
    }

    @Test fun authErrorEmitsFailedAuth() = runTest(UnconfinedTestDispatcher()) {
        val loop = loop(FakeClaudeClient({ throw ClaudeError.Auth() }), driver)
        val events = collect(loop)
        loop.run("안녕")
        assertEquals(AgentEvent.Failed("API 키를 확인하세요.", isAuth = true), events.last())
    }

    @Test fun networkErrorKeepsHistoryRetryable() = runTest(UnconfinedTestDispatcher()) {
        val client = FakeClaudeClient(
            { toolUse("tu_1", "read_page", "{}") }, { throw ClaudeError.Network() }, { text("끝") },
        )
        val loop = loop(client, driver)
        val events = collect(loop)
        loop.run("읽어줘")
        assertEquals(AgentEvent.Failed("연결이 끊겼어요.", isAuth = false), events.last())
        loop.run("다시")
        assertTrue(rolesAlternate(client.calls[2]))
        assertTrue(everyToolUseAnswered(client.calls[2]))
        assertEquals(AgentEvent.Done, events.last())
    }

    @Test fun failedFirstCallDoesNotDuplicateUserText() = runTest(UnconfinedTestDispatcher()) {
        val client = FakeClaudeClient({ throw ClaudeError.Network() }, { text("끝") })
        val loop = loop(client, driver)
        collect(loop)
        loop.run("안녕")
        loop.run("안녕")
        assertEquals(listOf(Message("user", listOf(Block.Text("안녕")))), client.calls[1])
    }

    @Test fun cancelLeavesNoDanglingToolUse() = runTest(UnconfinedTestDispatcher()) {
        driver.clickGate = CompletableDeferred()
        val client = FakeClaudeClient({ toolUse("tu_1", "click", """{"ref":1}""") }, { text("끝") })
        val loop = loop(client, driver)
        collect(loop)
        val job = launch { loop.run("눌러줘") }
        driver.clickStarted.await()
        job.cancelAndJoin()
        assertEquals(null, driver.highlights.last())
        loop.run("이어서")
        assertTrue(rolesAlternate(client.calls[1]))
        assertTrue(everyToolUseAnswered(client.calls[1]))
    }

    @Test fun resetClearsHistory() = runTest(UnconfinedTestDispatcher()) {
        val client = FakeClaudeClient({ text("a") }, { text("b") })
        val loop = loop(client, driver)
        collect(loop)
        loop.run("하나")
        loop.reset()
        loop.run("둘")
        assertEquals(listOf(Message("user", listOf(Block.Text("둘")))), client.calls[1])
    }
}
