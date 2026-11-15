package dev.agentbrowser.chat

import dev.agentbrowser.agent.AgentLoop
import dev.agentbrowser.agent.ClaudeError
import dev.agentbrowser.agent.FakeClaudeClient
import dev.agentbrowser.agent.FakePageDriver
import dev.agentbrowser.agent.text
import dev.agentbrowser.agent.toolUse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val driver = FakePageDriver()

    private fun TestScope.model(client: FakeClaudeClient, maxSteps: Int = 25) =
        ChatViewModel(AgentLoop(client, driver, maxSteps), backgroundScope)

    @Test fun userAndAssistantTextBecomeItems() = runTest(UnconfinedTestDispatcher()) {
        val model = model(FakeClaudeClient({ text("안녕하세요") }))
        model.send("안녕")
        assertEquals(listOf(ChatItem.User("안녕"), ChatItem.Assistant("안녕하세요")), model.items.value)
        assertFalse(model.running.value)
    }

    @Test fun actionEventBecomesActionItem() = runTest(UnconfinedTestDispatcher()) {
        val model = model(FakeClaudeClient({ toolUse("tu_1", "read_page", "{}") }, { text("끝") }))
        model.send("읽어줘")
        assertEquals(
            listOf(ChatItem.User("읽어줘"), ChatItem.Action("페이지 읽기"), ChatItem.Assistant("끝")),
            model.items.value,
        )
    }

    @Test fun confirmRequestSetsPendingAndClearsOnAnswer() = runTest(UnconfinedTestDispatcher()) {
        driver.labels[1] = "결제하기"
        val model = model(FakeClaudeClient({ toolUse("tu_1", "click", """{"ref":1}""") }, { text("끝") }))
        model.send("결제해줘")
        assertEquals("'결제하기' 클릭", model.pendingConfirm.value)
        assertTrue(model.running.value)
        assertEquals(emptyList<Int>(), driver.clicks)
        model.answerConfirm(true)
        assertNull(model.pendingConfirm.value)
        assertEquals(listOf(1), driver.clicks)
        assertFalse(model.running.value)
    }

    @Test fun authFailureAddsNoticeWithSettingsLink() = runTest(UnconfinedTestDispatcher()) {
        val model = model(FakeClaudeClient({ throw ClaudeError.Auth() }))
        model.send("안녕")
        assertEquals(ChatItem.Notice("API 키를 확인하세요.", openSettings = true), model.items.value.last())
    }

    @Test fun networkFailureAddsNotice() = runTest(UnconfinedTestDispatcher()) {
        val model = model(FakeClaudeClient({ throw ClaudeError.Network() }))
        model.send("안녕")
        assertEquals(ChatItem.Notice("연결이 끊겼어요.", openSettings = false), model.items.value.last())
    }

    @Test fun stepLimitAddsNotice() = runTest(UnconfinedTestDispatcher()) {
        var n = 0
        val client = FakeClaudeClient().apply { fallback = { toolUse("tu_${n++}", "read_page", "{}") } }
        val model = model(client, maxSteps = 2)
        model.send("계속 읽어")
        assertEquals(
            ChatItem.Notice("한 번에 할 수 있는 동작 수를 넘어 멈췄어요.", openSettings = false),
            model.items.value.last(),
        )
    }

    @Test fun stopCancelsAndSetsRunningFalse() = runTest(UnconfinedTestDispatcher()) {
        driver.clickGate = CompletableDeferred()
        val model = model(FakeClaudeClient({ toolUse("tu_1", "click", """{"ref":1}""") }))
        model.send("눌러줘")
        assertTrue(model.running.value)
        model.stop()
        assertFalse(model.running.value)
        assertEquals(ChatItem.Notice("중지했어요.", openSettings = false), model.items.value.last())
    }

    @Test fun sendWhileRunningIsIgnored() = runTest(UnconfinedTestDispatcher()) {
        driver.clickGate = CompletableDeferred()
        val model = model(FakeClaudeClient({ toolUse("tu_1", "click", """{"ref":1}""") }))
        model.send("눌러줘")
        model.send("하나 더")
        assertEquals(1, model.items.value.count { it is ChatItem.User })
    }

    @Test fun blankTextIsIgnored() = runTest(UnconfinedTestDispatcher()) {
        val model = model(FakeClaudeClient())
        model.send("   ")
        assertEquals(emptyList<ChatItem>(), model.items.value)
    }

    @Test fun newChatClearsItemsAndHistory() = runTest(UnconfinedTestDispatcher()) {
        val client = FakeClaudeClient({ text("a") }, { text("b") })
        val model = model(client)
        model.send("하나")
        model.newChat()
        assertEquals(emptyList<ChatItem>(), model.items.value)
        model.send("둘")
        assertEquals(1, client.calls[1].size)
    }
}
