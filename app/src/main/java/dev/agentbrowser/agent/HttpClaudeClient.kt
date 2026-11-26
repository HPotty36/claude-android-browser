package dev.agentbrowser.agent

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val MAX_TOKENS = 4096

class HttpClaudeClient(
    private val apiKey: () -> String?,
    private val model: () -> String,
    private val baseUrl: String = "https://api.anthropic.com",
    private val http: OkHttpClient = OkHttpClient.Builder().readTimeout(120, TimeUnit.SECONDS).build(),
    /** The longest reply asked for, in tokens. */
    private val maxTokens: Int = MAX_TOKENS,
) : ClaudeClient {

    override suspend fun send(system: String, messages: List<Message>, tools: List<ToolDef>): ClaudeReply {
        val key = apiKey()?.takeIf { it.isNotBlank() } ?: throw ClaudeError.Auth()
        val body = buildJsonObject {
            put("model", model())
            put("max_tokens", maxTokens)
            put("system", system)
            put("messages", buildJsonArray { messages.forEach { add(encodeMessage(it)) } })
            if (tools.isNotEmpty()) put("tools", buildJsonArray { tools.forEach { add(encodeTool(it)) } })
        }
        val request = Request.Builder()
            .url("$baseUrl/v1/messages")
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val (status, text) = execute(request)
        if (status == 401 || status == 403) throw ClaudeError.Auth()
        if (status !in 200..299) throw ClaudeError.Api(status, errorMessage(text) ?: "Request failed (HTTP $status).")
        return decodeReply(text)
    }

    /** Runs the call off the caller's thread and cancels it when the coroutine is cancelled. */
    private suspend fun execute(request: Request): Pair<Int, String> = suspendCancellableCoroutine { cont ->
        val call = http.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(ClaudeError.Network())
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use { it.code to it.body?.string().orEmpty() }
                } catch (e: IOException) {
                    if (cont.isActive) cont.resumeWithException(ClaudeError.Network())
                    return
                }
                if (cont.isActive) cont.resume(result)
            }
        })
    }

    private fun errorMessage(text: String): String? = runCatching {
        Json.parseToJsonElement(text).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
    }.getOrNull()

    private fun decodeReply(text: String): ClaudeReply {
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw ClaudeError.Api(200, "The response could not be read.")
        }
        val blocks = (root["content"] as? JsonArray).orEmpty().mapNotNull { element ->
            val block = element.jsonObject
            when (block["type"]?.jsonPrimitive?.content) {
                "text" -> Block.Text(block["text"]!!.jsonPrimitive.content)
                "tool_use" -> Block.ToolUse(
                    id = block["id"]!!.jsonPrimitive.content,
                    name = block["name"]!!.jsonPrimitive.content,
                    input = block["input"] as? JsonObject ?: JsonObject(emptyMap()),
                )
                else -> null
            }
        }
        return ClaudeReply(blocks, root["stop_reason"]?.jsonPrimitive?.content.orEmpty())
    }

    private fun encodeTool(tool: ToolDef) = buildJsonObject {
        put("name", tool.name)
        put("description", tool.description)
        put("input_schema", tool.inputSchema)
    }

    private fun encodeMessage(message: Message) = buildJsonObject {
        put("role", message.role)
        put("content", buildJsonArray { message.content.forEach { add(encodeBlock(it)) } })
    }

    private fun encodeBlock(block: Block): JsonObject = when (block) {
        is Block.Text -> buildJsonObject {
            put("type", "text")
            put("text", block.text)
        }
        is Block.Image -> buildJsonObject {
            put("type", "image")
            put("source", buildJsonObject {
                put("type", "base64")
                put("media_type", "image/jpeg")
                put("data", Base64.getEncoder().encodeToString(block.jpeg))
            })
        }
        is Block.ToolUse -> buildJsonObject {
            put("type", "tool_use")
            put("id", block.id)
            put("name", block.name)
            put("input", block.input)
        }
        is Block.ToolResult -> buildJsonObject {
            put("type", "tool_result")
            put("tool_use_id", block.toolUseId)
            put("content", buildJsonArray { block.content.forEach { add(encodeBlock(it)) } })
            if (block.isError) put("is_error", true)
        }
    }
}
