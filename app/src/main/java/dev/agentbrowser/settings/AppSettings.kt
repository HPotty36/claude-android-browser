package dev.agentbrowser.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val DEFAULT_MODEL = "claude-sonnet-5-5"
/** Model ID to the name shown in settings. */
val KNOWN_MODELS = listOf(
    "claude-sonnet-5-5" to "Sonnet 5.5",
    "claude-opus-5-5" to "Opus 5.5",
    "claude-haiku-5-5" to "Haiku 5.5",
)

/** The API key (stored encrypted) and the model to use. */
class AppSettings(context: Context, private val secrets: SecretStore, prefsName: String = "settings") {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private var apiKey: String? = prefs.getString("api_key_enc", null)?.let { runCatching { secrets.decrypt(it) }.getOrNull() }
    private val _apiKeyHint = MutableStateFlow(hint())
    private val _model = MutableStateFlow(prefs.getString("model", null) ?: DEFAULT_MODEL)

    /** Last four characters of the saved key, or null when none is saved. */
    val apiKeyHint: StateFlow<String?> = _apiKeyHint
    val model: StateFlow<String> = _model

    private fun hint() = apiKey?.takeLast(4)

    fun setApiKey(key: String?) {
        apiKey = key?.trim()?.takeIf { it.isNotEmpty() }
        prefs.edit().apply {
            if (apiKey == null) remove("api_key_enc") else putString("api_key_enc", secrets.encrypt(apiKey!!))
        }.apply()
        _apiKeyHint.value = hint()
    }

    fun setModel(model: String) {
        val value = model.trim().ifEmpty { DEFAULT_MODEL }
        prefs.edit().putString("model", value).apply()
        _model.value = value
    }

    fun apiKeyBlocking(): String? = apiKey
    fun modelBlocking(): String = _model.value
}
