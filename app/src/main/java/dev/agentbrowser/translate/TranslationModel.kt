package dev.agentbrowser.translate

import dev.agentbrowser.agent.ClaudeError

/**
 * The model translations are asked of: [primary], until the API says it does not know it (a model retired after
 * this version shipped). From then on the model the user chose for chat, which their key can use.
 */
class TranslationModel(private val primary: String, private val fallback: () -> String) {
    @Volatile private var retired = false

    fun current(): String = if (retired) fallback() else primary

    /** Runs [request]; when it fails because the model is unknown, switches to the fallback and runs it once more. */
    suspend fun <T> withFallback(request: suspend () -> T): T = try {
        request()
    } catch (e: ClaudeError.Api) {
        if (e.status != 404 || retired) throw e
        retired = true
        request()
    }
}
