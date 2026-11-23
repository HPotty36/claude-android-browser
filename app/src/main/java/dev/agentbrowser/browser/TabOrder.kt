package dev.agentbrowser.browser

/**
 * Which tab is current once [closing] is gone: the current one if it survives, otherwise the tab
 * that sat before the closed one (or after it, for the first). Null when no tab is left.
 */
fun currentAfterClose(ids: List<Long>, current: Long, closing: Long): Long? {
    val index = ids.indexOf(closing)
    if (index < 0 || closing != current) return current
    val remaining = ids - closing
    return remaining.getOrNull((index - 1).coerceAtLeast(0))
}

/** The tab [step] places along from the current one, wrapping round at either end. */
fun neighbourTab(ids: List<Long>, current: Long, step: Int): Long {
    val index = ids.indexOf(current)
    if (index < 0 || ids.isEmpty()) return current
    return ids[Math.floorMod(index + step, ids.size)]
}

/**
 * Whether a link handed over by another app needs a tab of its own: it does unless the current tab shows
 * nothing yet and has nothing waiting to load ([waitingUrl], a link that came while the engine was starting).
 */
fun linkNeedsNewTab(shownUrl: String, waitingUrl: String?): Boolean =
    waitingUrl != null || (shownUrl.isNotEmpty() && shownUrl != HOME_URL)
