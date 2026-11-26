package dev.agentbrowser.browser

private val ENGINE_SCHEMES = setOf(
    "http", "https", "about", "data", "blob", "file", "javascript", "view-source",
    "moz-extension", "resource", "chrome", "jar", "ws", "wss",
)
private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")

private fun schemeOf(uri: String): String? = SCHEME.find(uri.trim())?.groupValues?.get(1)?.lowercase()

/** Whether the engine shows this address itself. Anything else (myapp://, intent://, mailto:) is meant for an app. */
fun isWebAddress(uri: String): Boolean = schemeOf(uri)?.let { it in ENGINE_SCHEMES } ?: true

/**
 * Whether a link is an ordinary web page. Only those are offered in the link menu: a page's links to file:,
 * resource: or javascript: addresses are not to be opened or copied from there with the app's own rights.
 */
fun isHttpLink(uri: String): Boolean = schemeOf(uri).let { it == "http" || it == "https" }

/**
 * What to show when asking whether to leave for an app: the app an intent link names, or the link's scheme and
 * host. The rest of the link is left out, since a sign-in hands its code back in it.
 */
fun externalLinkLabel(uri: String): String {
    val link = uri.trim()
    val scheme = schemeOf(link) ?: return link
    if (scheme == "intent") {
        val parameters = link.substringAfter("#Intent;", "").split(';')
        parameters.firstOrNull { it.startsWith("package=") }?.let { return it.removePrefix("package=") }
        val target = parameters.firstOrNull { it.startsWith("scheme=") }?.removePrefix("scheme=") ?: return "intent:"
        return "$target://${hostOf(link)}"
    }
    return if (link.contains("://")) "$scheme://${hostOf(link)}" else "$scheme:"
}

private fun hostOf(link: String): String = link.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' }
