package dev.agentbrowser.page

import android.graphics.Bitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import java.io.ByteArrayOutputStream

private const val TIMEOUT_MS = 10_000L
private const val NO_RESPONSE = "페이지가 응답하지 않습니다."
private const val SCREENSHOT_MAX_SIDE = 1024

/**
 * Drives the page through the built-in extension's content script. Each page load opens a new port,
 * so commands wait for the current page's port and treat a port that closes mid-command as navigation.
 *
 * [capture] returns the visible page as a bitmap, or null when no view is attached.
 */
class GeckoPageDriver(
    private val session: GeckoSession,
    private val capture: suspend () -> Bitmap?,
) : PageDriver, WebExtension.MessageDelegate, WebExtension.PortDelegate {

    private class Pending(val port: WebExtension.Port, val reply: CompletableDeferred<JSONObject?>)

    private val port = MutableStateFlow<WebExtension.Port?>(null)
    private val pending = mutableMapOf<Int, Pending>()
    private var nextId = 1

    // Delegate callbacks arrive on the main thread, the same thread every command runs on.
    override fun onConnect(port: WebExtension.Port) {
        port.setDelegate(this)
        this.port.value = port
    }

    override fun onDisconnect(port: WebExtension.Port) {
        if (this.port.value === port) this.port.value = null
        pending.entries.filter { it.value.port === port }.forEach {
            pending.remove(it.key)
            it.value.reply.complete(null)
        }
    }

    override fun onPortMessage(message: Any, port: WebExtension.Port) {
        val json = message as? JSONObject ?: return
        pending.remove(json.optInt("id"))?.reply?.complete(json.optJSONObject("result") ?: JSONObject())
    }

    private sealed interface Reply {
        data class Result(val json: JSONObject) : Reply
        data object Navigated : Reply
        data object NoResponse : Reply
    }

    private suspend fun call(cmd: String, args: JSONObject = JSONObject(), timeoutMs: Long = TIMEOUT_MS): Reply =
        withContext(Dispatchers.Main) {
            val current = withTimeoutOrNull(timeoutMs) { port.filterNotNull().first() }
                ?: return@withContext Reply.NoResponse
            val id = nextId++
            val reply = CompletableDeferred<JSONObject?>()
            pending[id] = Pending(current, reply)
            try {
                current.postMessage(JSONObject().put("id", id).put("cmd", cmd).put("args", args))
                val answered = withTimeoutOrNull(timeoutMs) { Reply.Result(reply.await() ?: return@withTimeoutOrNull Reply.Navigated) }
                answered ?: Reply.NoResponse
            } finally {
                pending.remove(id)
            }
        }

    private fun toAction(reply: Reply): ActionResult = when (reply) {
        is Reply.Result -> {
            val error = reply.json.optString("error")
            if (error.isNotEmpty()) ActionResult.Error(error) else ActionResult.Ok(reply.json.optString("ok"))
        }
        Reply.Navigated -> ActionResult.Ok("실행했고 페이지가 이동했습니다. 페이지를 다시 읽으세요.")
        Reply.NoResponse -> ActionResult.Error(NO_RESPONSE)
    }

    override suspend fun snapshot(): PageSnapshot {
        repeat(2) {
            val reply = call("snapshot")
            if (reply is Reply.Result) {
                return PageSnapshot(
                    url = reply.json.optString("url"),
                    title = reply.json.optString("title"),
                    text = reply.json.optString("text"),
                    hasPasswordField = reply.json.optBoolean("hasPasswordField"),
                )
            }
        }
        return PageSnapshot("", "", "$NO_RESPONSE 페이지가 아직 열리는 중이거나 읽을 수 없는 페이지입니다.", false)
    }

    private suspend fun info(ref: Int): JSONObject? =
        (call("info", JSONObject().put("ref", ref)) as? Reply.Result)?.json?.takeIf { !it.has("error") }

    override suspend fun elementLabel(ref: Int): String? = info(ref)?.optString("label")
    override suspend fun elementKind(ref: Int): String? = info(ref)?.optString("kind")

    override suspend fun click(ref: Int): ActionResult =
        toAction(call("click", JSONObject().put("ref", ref))).also { settle() }

    override suspend fun type(ref: Int, text: String, submit: Boolean): ActionResult =
        toAction(call("type", JSONObject().put("ref", ref).put("text", text).put("submit", submit)))
            .also { if (submit) settle() }

    override suspend fun scroll(direction: String): ActionResult =
        toAction(call("scroll", JSONObject().put("direction", direction)))

    override suspend fun navigate(target: String): ActionResult = withContext(Dispatchers.Main) {
        when {
            target == "back" -> session.goBack()
            target == "forward" -> session.goForward()
            target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true) ->
                session.loadUri(target)
            else -> return@withContext ActionResult.Error("http 또는 https 주소, \"back\", \"forward\"만 쓸 수 있습니다.")
        }
        settle()
        ActionResult.Ok("이동했습니다. 페이지를 다시 읽으세요.")
    }

    override suspend fun screenshot(): ByteArray? {
        if (snapshot().hasPasswordField) return null
        val bitmap = withContext(Dispatchers.Main) { capture() } ?: return null
        return withContext(Dispatchers.Default) { toJpeg(bitmap) }
    }

    override suspend fun highlight(ref: Int?) {
        call("highlight", JSONObject().put("ref", ref ?: JSONObject.NULL), timeoutMs = 1_000)
    }

    /** Gives a navigation started by the last action a moment to begin, so the next read sees the new page. */
    private suspend fun settle() = delay(700)

    private fun toJpeg(bitmap: Bitmap): ByteArray {
        val longSide = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longSide <= SCREENSHOT_MAX_SIDE) bitmap else {
            val ratio = SCREENSHOT_MAX_SIDE.toFloat() / longSide
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        }
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
    }
}
