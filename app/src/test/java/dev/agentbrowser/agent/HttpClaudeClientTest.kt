package dev.agentbrowser.agent

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpClaudeClientTest {
    private val server = MockWebServer()
    private val tool = ToolDef("read_page", "reads the page", buildJsonObject { put("type", "object") })
    private val textReply = """{"content":[{"type":"text","text":"hi"}],"stop_reason":"end_turn"}"""

    @Before fun start() = server.start()
    @After fun stop() { runCatching { server.shutdown() } }

    private fun client(key: String? = "sk-test") =
        HttpClaudeClient({ key }, { "claude-sonnet-5-5" }, server.url("/").toString().trimEnd('/'))

    private fun userText(text: String) = listOf(Message("user", listOf(Block.Text(text))))
    private fun ok(body: String) = MockResponse().setBody(body).setHeader("content-type", "application/json")
    private fun sentBody(): JsonObject =
        Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    private fun sendOnce(client: ClaudeClient) {
        runBlocking { client.send("s", userText("x"), listOf(tool)) }
    }

    @Test fun sendsHeadersAndBody() = runBlocking {
        server.enqueue(ok(textReply))
        client().send("sys", userText("hello"), listOf(tool))
        val request = server.takeRequest()
        assertEquals("/v1/messages", request.path)
        assertEquals("sk-test", request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("claude-sonnet-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals(4096, body["max_tokens"]!!.jsonPrimitive.int)
        assertEquals("sys", body["system"]!!.jsonPrimitive.content)
        assertTrue(body["tools"]!!.jsonArray[0].jsonObject.containsKey("input_schema"))
        val first = body["messages"]!!.jsonArray[0].jsonObject
        assertEquals("user", first["role"]!!.jsonPrimitive.content)
        assertEquals("hello", first["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test fun parsesTextAndToolUse() = runBlocking {
        server.enqueue(ok("""{"content":[{"type":"text","text":"클릭할게요"},
            {"type":"tool_use","id":"tu_1","name":"click","input":{"ref":3}}],"stop_reason":"tool_use"}"""))
        val reply = client().send("sys", userText("go"), listOf(tool))
        assertEquals("tool_use", reply.stopReason)
        assertEquals(Block.Text("클릭할게요"), reply.content[0])
        val use = reply.content[1] as Block.ToolUse
        assertEquals("tu_1", use.id)
        assertEquals("click", use.name)
        assertEquals(3, use.input["ref"]!!.jsonPrimitive.int)
    }

    @Test fun encodesToolResultWithImage() = runBlocking {
        server.enqueue(ok(textReply))
        val result = Block.ToolResult("tu_1", listOf(Block.Text("shot"), Block.Image(byteArrayOf(1, 2, 3))), isError = true)
        client().send("sys", listOf(Message("user", listOf(result))), listOf(tool))
        val block = sentBody()["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray[0].jsonObject
        assertEquals("tool_result", block["type"]!!.jsonPrimitive.content)
        assertEquals("tu_1", block["tool_use_id"]!!.jsonPrimitive.content)
        assertEquals("true", block["is_error"]!!.jsonPrimitive.content)
        val image = block["content"]!!.jsonArray[1].jsonObject
        assertEquals("image", image["type"]!!.jsonPrimitive.content)
        val source = image["source"]!!.jsonObject
        assertEquals("base64", source["type"]!!.jsonPrimitive.content)
        assertEquals("image/jpeg", source["media_type"]!!.jsonPrimitive.content)
        assertEquals("AQID", source["data"]!!.jsonPrimitive.content)
    }

    @Test fun status401IsAuth() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"invalid x-api-key"}}"""))
        assertThrows(ClaudeError.Auth::class.java) { sendOnce(client()) }
    }

    @Test fun missingKeyIsAuth() {
        assertThrows(ClaudeError.Auth::class.java) { sendOnce(client(key = null)) }
        assertEquals(0, server.requestCount)
    }

    @Test fun ioFailureIsNetwork() {
        val client = client()
        server.shutdown()
        assertThrows(ClaudeError.Network::class.java) { sendOnce(client) }
    }

    @Test fun status529IsApi() {
        server.enqueue(MockResponse().setResponseCode(529).setBody("""{"error":{"message":"Overloaded"}}"""))
        val error = assertThrows(ClaudeError.Api::class.java) { sendOnce(client()) }
        assertEquals(529, error.status)
        assertEquals("Overloaded", error.message)
    }

    @Test fun theReplySizeLimitCanBeRaised() = runBlocking {
        server.enqueue(ok(textReply))
        HttpClaudeClient({ "sk-test" }, { "m" }, server.url("/").toString().trimEnd('/'), maxTokens = 8_192)
            .send("s", userText("x"), emptyList())
        assertEquals(8_192, sentBody()["max_tokens"]!!.jsonPrimitive.int)
    }
}
