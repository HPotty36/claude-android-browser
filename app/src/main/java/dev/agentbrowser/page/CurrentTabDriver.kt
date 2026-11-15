package dev.agentbrowser.page

/** A [PageDriver] that always acts on whichever tab is current at the moment of the call. */
class CurrentTabDriver(private val current: () -> PageDriver) : PageDriver {
    override suspend fun snapshot() = current().snapshot()
    override suspend fun elementLabel(ref: Int) = current().elementLabel(ref)
    override suspend fun elementKind(ref: Int) = current().elementKind(ref)
    override suspend fun click(ref: Int) = current().click(ref)
    override suspend fun type(ref: Int, text: String, submit: Boolean) = current().type(ref, text, submit)
    override suspend fun scroll(direction: String) = current().scroll(direction)
    override suspend fun navigate(target: String) = current().navigate(target)
    override suspend fun screenshot() = current().screenshot()
    override suspend fun highlight(ref: Int?) = current().highlight(ref)
}
