package dev.agentbrowser.chat

/** Where the chat panel comes to rest when a drag ends. */
sealed interface PanelRest {
    data object Closed : PanelRest
    data object Full : PanelRest
    data class Partial(val fraction: Float) : PanelRest
}

const val PANEL_DEFAULT = 0.5f
private const val PANEL_MIN = 0.25f
private const val CLOSE_BELOW = 0.15f
private const val FULL_ABOVE = 0.9f

/**
 * The panel's share of the area after its top edge is dragged by [dragPx] (negative is up).
 * [areaPx] is the height shared by the page and the panel.
 */
fun dragPanel(fraction: Float, dragPx: Float, areaPx: Float): Float {
    if (areaPx <= 0f) return fraction
    return (fraction - dragPx / areaPx).coerceIn(0f, 1f)
}

/** Let go near the top and the panel takes the screen; near the bottom and it closes. */
fun restPanel(fraction: Float): PanelRest = when {
    fraction > FULL_ABOVE -> PanelRest.Full
    fraction < CLOSE_BELOW -> PanelRest.Closed
    else -> PanelRest.Partial(fraction.coerceAtLeast(PANEL_MIN))
}

/**
 * Whether the panel sits beside the page instead of under it: a window that is wider than tall and wide enough
 * for both. A monitor, a tablet or a phone on its side has height to spare least of all.
 */
fun panelBeside(widthDp: Float, heightDp: Float): Boolean = widthDp >= 720f && widthDp > heightDp

/** The width of the panel when it sits beside the page: never more than 45% of the window, 400dp at most. */
fun sidePanelWidth(widthDp: Float): Float = minOf(400f, widthDp * 0.45f)
