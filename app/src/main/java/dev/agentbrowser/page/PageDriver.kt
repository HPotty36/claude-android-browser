package dev.agentbrowser.page

data class PageSnapshot(val url: String, val title: String, val text: String, val hasPasswordField: Boolean)

sealed interface ActionResult {
    data class Ok(val detail: String) : ActionResult
    data class Error(val message: String) : ActionResult
}

/** Reads and operates the page in the current tab. Element refs come from the latest [snapshot]. */
interface PageDriver {
    suspend fun snapshot(): PageSnapshot
    /** Label of the element, or null when the ref is unknown. */
    suspend fun elementLabel(ref: Int): String?
    /** Kind of the element ("button", "textbox", "password", ...), or null when the ref is unknown. */
    suspend fun elementKind(ref: Int): String?
    suspend fun click(ref: Int): ActionResult
    suspend fun type(ref: Int, text: String, submit: Boolean): ActionResult
    /** [direction] is "up" or "down". */
    suspend fun scroll(direction: String): ActionResult
    /** [target] is a URL, "back" or "forward". */
    suspend fun navigate(target: String): ActionResult
    /** JPEG bytes, or null when the page shows a password field. */
    suspend fun screenshot(): ByteArray?
    /** Outlines the element; null clears the outline. */
    suspend fun highlight(ref: Int?)
}
