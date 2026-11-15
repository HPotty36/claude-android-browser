package dev.agentbrowser.agent

import dev.agentbrowser.page.ActionResult
import dev.agentbrowser.page.PageDriver
import dev.agentbrowser.ui.En
import dev.agentbrowser.ui.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

sealed interface AgentEvent {
    data class AssistantText(val text: String) : AgentEvent
    data class Action(val description: String) : AgentEvent
    data class ConfirmRequest(val description: String) : AgentEvent
    data class Failed(val message: String, val isAuth: Boolean) : AgentEvent
    data object StepLimit : AgentEvent
    data object Done : AgentEvent
}

/**
 * Sends the conversation to Claude, runs the tools it asks for through [driver], and repeats until
 * Claude stops asking, [maxSteps] tools have run, or the calling coroutine is cancelled.
 */
class AgentLoop(
    private val client: ClaudeClient,
    private val driver: PageDriver,
    private val maxSteps: Int = 25,
    /** The language of what the user sees: action descriptions and failure notices. */
    private val strings: () -> Strings = { En },
) {
    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events

    private val history = mutableListOf<Message>()
    private var pendingConfirm: CompletableDeferred<Boolean>? = null

    fun confirm(approved: Boolean) {
        pendingConfirm?.complete(approved)
    }

    fun reset() {
        history.clear()
    }

    suspend fun run(userText: String) {
        val sizeBefore = history.size
        val lastBefore = history.lastOrNull()
        appendUser(listOf(Block.Text(userText)))
        var executed = 0
        var pendingUses: List<Block.ToolUse> = emptyList()
        val pendingResults = mutableListOf<Block>()
        try {
            while (true) {
                val reply = client.send(systemPrompt(strings().replyLanguage), history.toList(), TOOLS)
                if (reply.content.isNotEmpty()) history += Message("assistant", reply.content)
                reply.content.filterIsInstance<Block.Text>().filter { it.text.isNotBlank() }
                    .forEach { _events.emit(AgentEvent.AssistantText(it.text)) }

                pendingUses = reply.content.filterIsInstance<Block.ToolUse>()
                if (pendingUses.isEmpty()) {
                    _events.emit(AgentEvent.Done)
                    return
                }
                pendingResults.clear()
                for (use in pendingUses) {
                    if (executed >= maxSteps) {
                        pendingResults += error(use, "Not run: the action limit for this request was reached.")
                        continue
                    }
                    executed++
                    pendingResults += execute(use)
                }
                history += Message("user", pendingResults.toList())
                pendingUses = emptyList()
                if (executed >= maxSteps) {
                    _events.emit(AgentEvent.StepLimit)
                    return
                }
            }
        } catch (e: CancellationException) {
            closeOpenTurn(pendingUses, pendingResults, sizeBefore, lastBefore)
            throw e
        } catch (e: ClaudeError) {
            closeOpenTurn(pendingUses, pendingResults, sizeBefore, lastBefore)
            val message = when (e) {
                is ClaudeError.Auth -> strings().checkApiKey
                is ClaudeError.Network -> strings().connectionLost
                is ClaudeError.Api -> e.message.orEmpty()
            }
            _events.emit(AgentEvent.Failed(message, isAuth = e is ClaudeError.Auth))
        } finally {
            pendingConfirm = null
            withContext(NonCancellable) { runCatching { driver.highlight(null) } }
        }
    }

    /** Adds a user turn, merging into a trailing user message so roles keep alternating. */
    private fun appendUser(blocks: List<Block>) {
        val last = history.lastOrNull()
        if (last?.role == "user") {
            history[history.lastIndex] = Message("user", last.content + blocks)
        } else {
            history += Message("user", blocks)
        }
    }

    /**
     * Leaves the history valid after a stop or failure: every tool_use gets a tool_result, and a run
     * that never got a reply is rolled back so retrying does not repeat the user's text.
     */
    private fun closeOpenTurn(
        uses: List<Block.ToolUse>,
        results: List<Block>,
        sizeBefore: Int,
        lastBefore: Message?,
    ) {
        if (uses.isNotEmpty()) {
            val answered = results.filterIsInstance<Block.ToolResult>().map { it.toolUseId }.toSet()
            val missing = uses.filter { it.id !in answered }.map { error(it, "The user stopped the task.") }
            history += Message("user", results + missing)
            return
        }
        val noReplyYet = history.size == sizeBefore + 1 || (history.size == sizeBefore && lastBefore?.role == "user")
        if (noReplyYet) {
            if (history.size == sizeBefore + 1) history.removeAt(history.lastIndex)
            else history[history.lastIndex] = lastBefore!!
        }
    }

    private fun ok(use: Block.ToolUse, text: String) = Block.ToolResult(use.id, listOf(Block.Text(text)))
    private fun error(use: Block.ToolUse, text: String) =
        Block.ToolResult(use.id, listOf(Block.Text(text)), isError = true)

    private fun result(use: Block.ToolUse, outcome: ActionResult) = when (outcome) {
        is ActionResult.Ok -> ok(use, outcome.detail)
        is ActionResult.Error -> error(use, outcome.message)
    }

    private fun JsonObject.int(name: String) = runCatching { this[name]?.jsonPrimitive?.intOrNull }.getOrNull()
    private fun JsonObject.string(name: String) = runCatching { this[name]?.jsonPrimitive?.contentOrNull }.getOrNull()
    private fun JsonObject.bool(name: String) =
        runCatching { this[name]?.jsonPrimitive?.booleanOrNull }.getOrNull() == true

    private suspend fun execute(use: Block.ToolUse): Block.ToolResult = try {
        when (use.name) {
            "read_page" -> {
                _events.emit(AgentEvent.Action(strings().actionReadPage))
                val page = driver.snapshot()
                ok(use, "URL: ${page.url}\nTitle: ${page.title}\n${page.text}")
            }
            "click" -> onElement(use) { ref, label ->
                act(use, ref, label, strings().actionClick(label)) { driver.click(ref) }
            }
            "type" -> onElement(use) { ref, label ->
                val text = use.input.string("text") ?: return@onElement error(use, "text is required.")
                if (driver.elementKind(ref) == "password") {
                    return@onElement error(use, "Passwords must be typed by the user themselves.")
                }
                val submit = use.input.bool("submit")
                val description = strings().actionType(label, text, submit)
                act(use, ref, label, description) { driver.type(ref, text, submit) }
            }
            "scroll" -> {
                val direction = if (use.input.string("direction") == "up") "up" else "down"
                _events.emit(AgentEvent.Action(if (direction == "up") strings().actionScrollUp else strings().actionScrollDown))
                result(use, driver.scroll(direction))
            }
            "navigate" -> {
                val target = use.input.string("target") ?: return error(use, "target is required.")
                _events.emit(AgentEvent.Action(strings().actionNavigate(target)))
                result(use, driver.navigate(target))
            }
            "screenshot" -> {
                _events.emit(AgentEvent.Action(strings().actionScreenshot))
                val jpeg = driver.screenshot()
                if (jpeg == null) ok(use, "A page showing a password field cannot be captured.")
                else Block.ToolResult(use.id, listOf(Block.Image(jpeg)))
            }
            else -> error(use, "Unknown tool: ${use.name}")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ClaudeError) {
        throw e
    } catch (e: Exception) {
        error(use, "The action failed: ${e.message}")
    }

    private suspend fun onElement(
        use: Block.ToolUse,
        block: suspend (ref: Int, label: String) -> Block.ToolResult,
    ): Block.ToolResult {
        val ref = use.input.int("ref") ?: return error(use, "ref is required.")
        return block(ref, driver.elementLabel(ref)?.takeIf { it.isNotBlank() } ?: strings().unnamedElement(ref))
    }

    /** Asks the user first when the action needs it, then highlights the element and runs [action]. */
    private suspend fun act(
        use: Block.ToolUse,
        ref: Int,
        label: String,
        description: String,
        action: suspend () -> ActionResult,
    ): Block.ToolResult {
        if (needsConfirmation(use.name, use.input, label)) {
            val answer = CompletableDeferred<Boolean>()
            pendingConfirm = answer
            driver.highlight(ref)
            _events.emit(AgentEvent.ConfirmRequest(description))
            val approved = answer.await()
            pendingConfirm = null
            if (!approved) return error(use, "The user declined. The action was not run.")
        }
        driver.highlight(ref)
        _events.emit(AgentEvent.Action(description))
        return result(use, action())
    }
}
