package dev.agentbrowser.browser

/** What the user picked in settings for asking sites for their desktop version. */
enum class DesktopSite(val id: String) { AUTO("auto"), ALWAYS("always"), NEVER("never") }

fun desktopSiteById(id: String?): DesktopSite = DesktopSite.entries.firstOrNull { it.id == id } ?: DesktopSite.AUTO

/** The narrowest side, in dp, from which a screen counts as large: tablets, unfolded foldables, external displays. */
const val LARGE_SCREEN_DP = 600

/**
 * Whether pages should be asked for their desktop version. [smallestWidthDp] is the screen's shorter side,
 * so turning a phone on its side does not count as a large screen.
 */
/** The shorter side of a screen of [widthPx] by [heightPx] pixels, in dp. */
fun smallestSideDp(widthPx: Int, heightPx: Int, density: Float): Int =
    if (density <= 0f) 0 else (minOf(widthPx, heightPx) / density).toInt()

fun wantsDesktopSite(choice: DesktopSite, smallestWidthDp: Int): Boolean = when (choice) {
    DesktopSite.ALWAYS -> true
    DesktopSite.NEVER -> false
    DesktopSite.AUTO -> smallestWidthDp >= LARGE_SCREEN_DP
}

private val WEB_URL = Regex("^(https?)://([^/?#@]+)(.*)$", RegexOption.IGNORE_CASE)
private val MOBILE_LABELS = setOf("m", "mobile")

/**
 * The desktop address of a mobile one, or null when the address is not a mobile one: m.naver.com to naver.com,
 * en.m.wikipedia.org to en.wikipedia.org. A site that keeps its phone pages on their own host name serves them
 * there whatever the browser says it is, so asking for the desktop version means going to the other host.
 */
fun desktopUrl(url: String): String? {
    val match = WEB_URL.find(url.trim()) ?: return null
    val (scheme, authority, rest) = match.destructured
    val host = authority.substringBefore(':').lowercase()
    val port = authority.substringAfter(':', "").let { if (it.isEmpty()) "" else ":$it" }
    val labels = host.split('.')
    val mobile = when {
        labels[0] in MOBILE_LABELS -> 0
        labels.size > 1 && labels[1] == "m" -> 1
        else -> return null
    }
    val desktop = labels.filterIndexed { index, _ -> index != mobile }
    if (desktop.size < 2) return null
    return "${scheme.lowercase()}://${desktop.joinToString(".")}$port$rest"
}
