package dev.agentbrowser.browser

import java.net.URI
import java.net.URLEncoder

/** Where anything that is not an address goes. */
private const val SEARCH_URL = "https://duckduckgo.com/?q="

private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")

/** Turns address-bar input into a URL: a full URL stays, a bare host gets https://, anything else is a search. */
fun toUrl(input: String): String {
    val text = input.trim()
    if (SCHEME.matches(text)) return text
    if (!text.contains(' ') && text.contains('.')) return "https://$text"
    return SEARCH_URL + URLEncoder.encode(text, "UTF-8")
}

/** What the address bar shows when it is not being edited: the host without "www.", or the input as is. */
fun displayHost(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull()
    val host = uri?.host ?: return url
    return host.removePrefix("www.") + if (uri.port >= 0) ":${uri.port}" else ""
}
