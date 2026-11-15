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
