package dev.agentbrowser.passwords

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate

/** Lets the engine fill login forms from [repo] and save new logins into it. */
class GeckoLoginStorage(private val repo: LoginRepository, private val scope: CoroutineScope) :
    Autocomplete.StorageDelegate {

    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>> {
        val result = GeckoResult<Array<Autocomplete.LoginEntry>>()
        scope.launch {
            val entries = runCatching { repo.forDomain(domain) }.getOrDefault(emptyList()).map { login ->
                Autocomplete.LoginEntry.Builder()
                    .origin(login.origin)
                    .formActionOrigin("") // empty matches a form posting anywhere
                    .username(login.username)
                    .password(login.password)
                    .build()
            }
            result.complete(entries.toTypedArray())
        }
        return result
    }

    override fun onLoginSave(login: Autocomplete.LoginEntry) {
        val origin = originOf(login.origin) ?: return
        if (login.password.isEmpty()) return
        scope.launch { runCatching { repo.save(ParsedLogin(origin, login.username, login.password)) } }
    }
}

/** A question from the page's login form that the screen has to show. */
sealed interface LoginPrompt {
    /** Offer to save [username] for [origin]; answer with [respond]. */
    class Save(val origin: String, val username: String, val respond: (save: Boolean) -> Unit) : LoginPrompt
    /** Let the user pick one of [usernames]; answer with the index, or null to dismiss. */
    class Select(val usernames: List<String>, val respond: (index: Int?) -> Unit) : LoginPrompt
}

/** Turns the engine's save-login and choose-login requests into [prompt] for the screen. */
class LoginPromptDelegate : PromptDelegate {
    private val _prompt = MutableStateFlow<LoginPrompt?>(null)
    val prompt: StateFlow<LoginPrompt?> = _prompt

    override fun onLoginSave(
        session: GeckoSession,
        request: PromptDelegate.AutocompleteRequest<Autocomplete.LoginSaveOption>,
    ): GeckoResult<PromptDelegate.PromptResponse>? {
        val option = request.options.firstOrNull() ?: return null
        val result = GeckoResult<PromptDelegate.PromptResponse>()
        _prompt.value = LoginPrompt.Save(option.value.origin, option.value.username) { save ->
            _prompt.value = null
            result.complete(if (save) request.confirm(option) else request.dismiss())
        }
        return result
    }

    override fun onLoginSelect(
        session: GeckoSession,
        request: PromptDelegate.AutocompleteRequest<Autocomplete.LoginSelectOption>,
    ): GeckoResult<PromptDelegate.PromptResponse>? {
        val options = request.options
        if (options.isEmpty()) return null
        val result = GeckoResult<PromptDelegate.PromptResponse>()
        _prompt.value = LoginPrompt.Select(options.map { it.value.username }) { index ->
            _prompt.value = null
            val chosen = index?.let { options.getOrNull(it) }
            result.complete(if (chosen != null) request.confirm(chosen) else request.dismiss())
        }
        return result
    }
}
