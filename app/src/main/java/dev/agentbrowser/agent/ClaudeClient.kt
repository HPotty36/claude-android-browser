package dev.agentbrowser.agent

import kotlinx.serialization.json.JsonObject

sealed interface Block {
    data class Text(val text: String) : Block
    class Image(val jpeg: ByteArray) : Block
    data class ToolUse(val id: String, val name: String, val input: JsonObject) : Block
    data class ToolResult(val toolUseId: String, val content: List<Block>, val isError: Boolean = false) : Block
}

/** [role] is "user" or "assistant". */
data class Message(val role: String, val content: List<Block>)

data class ToolDef(val name: String, val description: String, val inputSchema: JsonObject)

data class ClaudeReply(val content: List<Block>, val stopReason: String)

sealed class ClaudeError(message: String) : Exception(message) {
    class Auth : ClaudeError("The API key is missing or was rejected.")
    class Network : ClaudeError("The connection failed.")
    class Api(val status: Int, detail: String) : ClaudeError(detail)
}

interface ClaudeClient {
    suspend fun send(system: String, messages: List<Message>, tools: List<ToolDef>): ClaudeReply
}
