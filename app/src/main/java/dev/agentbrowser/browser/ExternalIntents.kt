package dev.agentbrowser.browser

import android.content.Context
import android.content.Intent
import android.net.Uri

private fun isIntentLink(uri: String) = uri.trim().startsWith("intent:", ignoreCase = true)

/**
 * The intent a link meant for an app asks for, or null when the link cannot be read. A page wrote the link, so
 * it may only reach what apps offer to browsers: it cannot pick a component inside an app.
 */
fun externalIntent(uri: String): Intent? = runCatching {
    val intent =
        if (isIntentLink(uri)) Intent.parseUri(uri.trim(), Intent.URI_INTENT_SCHEME)
        else Intent(Intent.ACTION_VIEW, Uri.parse(uri.trim()))
    intent.addCategory(Intent.CATEGORY_BROWSABLE)
    intent.component = null
    intent.selector = null
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}.getOrNull()

/** The web page an intent link offers for when its app is not installed. */
fun externalFallback(uri: String): String? {
    if (!isIntentLink(uri)) return null
    val fallback = runCatching { Intent.parseUri(uri.trim(), Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url") }.getOrNull()
    return fallback?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
}

/** Hands the link to an app. False when no installed app takes it. */
fun openExternal(context: Context, uri: String): Boolean {
    val intent = externalIntent(uri) ?: return false
    return runCatching { context.startActivity(intent) }.isSuccess
}
