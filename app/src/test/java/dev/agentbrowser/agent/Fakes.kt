package dev.agentbrowser.agent

import dev.agentbrowser.page.ActionResult
import dev.agentbrowser.page.PageDriver
import dev.agentbrowser.page.PageSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

internal fun toolUse(id: String, name: String, input: String) =
    ClaudeReply(listOf(Block.ToolUse(id, name, Json.parseToJsonElement(input).jsonObject)), "tool_use")

internal fun text(value: String) = ClaudeReply(listOf(Block.Text(value)), "end_turn")

internal class FakeClaudeClient(vararg steps: () -> ClaudeReply) : ClaudeClient {
    private val queue = ArrayDeque(steps.toList())
    var fallback: (() -> ClaudeReply)? = null
    val calls = mutableListOf<List<Message>>()

    override suspend fun send(system: String, messages: List<Message>, tools: List<ToolDef>): ClaudeReply {
        calls += messages.toList()
        val step = queue.removeFirstOrNull() ?: fallback ?: error("no scripted reply left")
        return step()
    }
}

internal class FakePageDriver : PageDriver {
    val labels = mutableMapOf<Int, String>()
    val kinds = mutableMapOf<Int, String>()
    var clickResult: ActionResult = ActionResult.Ok("클릭했습니다.")
    var clickGate: CompletableDeferred<Unit>? = null
    val clickStarted = CompletableDeferred<Unit>()
    val clicks = mutableListOf<Int>()
    val typed = mutableListOf<String>()
    val highlights = mutableListOf<Int?>()
    var snapshots = 0

    override suspend fun snapshot(): PageSnapshot {
        snapshots++
        return PageSnapshot("https://ex.com/", "Ex", "[1] button \"검색\"", false)
    }
    override suspend fun elementLabel(ref: Int) = labels[ref]
    override suspend fun elementKind(ref: Int) = kinds[ref]
    override suspend fun click(ref: Int): ActionResult {
        clicks += ref
        clickStarted.complete(Unit)
        clickGate?.await()
        return clickResult
    }
    override suspend fun type(ref: Int, text: String, submit: Boolean): ActionResult {
        typed += text
        return ActionResult.Ok("입력했습니다.")
    }
    override suspend fun scroll(direction: String): ActionResult = ActionResult.Ok("스크롤했습니다.")
    override suspend fun navigate(target: String): ActionResult = ActionResult.Ok("이동했습니다.")
    override suspend fun screenshot(): ByteArray? = null
    override suspend fun highlight(ref: Int?) { highlights += ref }
}


/** The loop under test, describing its actions in Korean so assertions can name them. */
internal fun loop(client: ClaudeClient, driver: PageDriver, maxSteps: Int = 25) =
    AgentLoop(client, driver, maxSteps) { dev.agentbrowser.ui.Ko }
