package dev.agentbrowser.settings

import android.content.Context
import dev.agentbrowser.browser.DesktopSite
import dev.agentbrowser.browser.SearchEngine
import dev.agentbrowser.browser.desktopSiteById
import dev.agentbrowser.browser.searchEngineById
import dev.agentbrowser.ui.Language
import dev.agentbrowser.ui.Strings
import dev.agentbrowser.ui.languageById
import dev.agentbrowser.ui.stringsFor
import java.util.Locale
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

    private val _searchEngine = MutableStateFlow(searchEngineById(prefs.getString("search_engine", null)))
    val searchEngine: StateFlow<SearchEngine> = _searchEngine

    fun setSearchEngine(id: String) {
        val engine = searchEngineById(id)
        prefs.edit().putString("search_engine", engine.id).apply()
        _searchEngine.value = engine
    }

    private val _language = MutableStateFlow(languageById(prefs.getString("language", null)))
    val language: StateFlow<Language> = _language

    fun setLanguage(language: Language) {
        prefs.edit().putString("language", language.id).apply()
        _language.value = language
    }

    private val _desktopSite = MutableStateFlow(desktopSiteById(prefs.getString("desktop_site", null)))
    val desktopSite: StateFlow<DesktopSite> = _desktopSite

    fun setDesktopSite(choice: DesktopSite) {
        prefs.edit().putString("desktop_site", choice.id).apply()
        _desktopSite.value = choice
    }

    private val _adBlock = MutableStateFlow(prefs.getBoolean("ad_block", true))
    val adBlock: StateFlow<Boolean> = _adBlock

    fun setAdBlock(on: Boolean) {
        prefs.edit().putBoolean("ad_block", on).apply()
        _adBlock.value = on
    }

    private val _encryptedDns = MutableStateFlow(prefs.getBoolean("encrypted_dns", true))
    val encryptedDns: StateFlow<Boolean> = _encryptedDns

    fun setEncryptedDns(on: Boolean) {
        prefs.edit().putBoolean("encrypted_dns", on).apply()
        _encryptedDns.value = on
    }

    /** The strings for the chosen language, read where there is no composition to provide them. */
    fun strings(): Strings = stringsFor(_language.value, Locale.getDefault().language)

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

    /** When the app last looked for an update by itself, in epoch milliseconds; 0 when it never has. */
    var lastUpdateCheck: Long
        get() = prefs.getLong("last_update_check", 0L)
        set(value) = prefs.edit().putLong("last_update_check", value).apply()

    fun apiKeyBlocking(): String? = apiKey
    fun modelBlocking(): String = _model.value
}
