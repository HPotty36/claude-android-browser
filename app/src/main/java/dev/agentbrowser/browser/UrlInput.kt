package dev.agentbrowser.browser

import java.net.URI
import java.net.URLEncoder

private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")

/**
 * Turns address-bar input into a URL: a full URL stays, a bare host gets https://, and anything else
 * is searched for at [searchUrl].
 */
fun toUrl(input: String, searchUrl: String = DEFAULT_SEARCH_ENGINE.queryUrl): String {
    val text = input.trim()
    if (SCHEME.matches(text)) return text
    if (!text.contains(' ') && text.contains('.')) return "https://$text"
    return searchUrl + URLEncoder.encode(text, "UTF-8")
}

/** What the address bar shows when it is not being edited: the host without "www.", or the input as is. */
fun displayHost(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull()
    val host = uri?.host ?: return url
    return host.removePrefix("www.") + if (uri.port >= 0) ":${uri.port}" else ""
}
