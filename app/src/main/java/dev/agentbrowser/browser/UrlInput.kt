package dev.agentbrowser.browser

import java.net.URLEncoder

private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")

/** Turns address-bar input into a URL: a full URL stays, a bare host gets https://, anything else is a search. */
fun toUrl(input: String): String {
    val text = input.trim()
    if (SCHEME.matches(text)) return text
    if (!text.contains(' ') && text.contains('.')) return "https://$text"
    return "https://www.google.com/search?q=" + URLEncoder.encode(text, "UTF-8")
}
