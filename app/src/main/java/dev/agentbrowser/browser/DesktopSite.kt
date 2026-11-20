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
fun wantsDesktopSite(choice: DesktopSite, smallestWidthDp: Int): Boolean = when (choice) {
    DesktopSite.ALWAYS -> true
    DesktopSite.NEVER -> false
    DesktopSite.AUTO -> smallestWidthDp >= LARGE_SCREEN_DP
}
