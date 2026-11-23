package dev.agentbrowser.browser

import android.app.Instrumentation
import android.os.SystemClock

/**
 * Leaves the current tab on a blank page that has stayed put for a second. A page an earlier test left loading
 * can land in the tab late (a redirect to an app commits its page half a second afterwards) and replace what a
 * test opens there. A new tab would avoid that, but one made after the app has gone to the background may never
 * finish loading, so the tab is reused instead. Call from the test thread.
 */
fun BrowserController.settle(instrumentation: Instrumentation) {
    instrumentation.runOnMainSync { load(HOME_URL) }
    val end = SystemClock.uptimeMillis() + 30_000
    var blankSince = 0L
    while (SystemClock.uptimeMillis() < end) {
        val now = SystemClock.uptimeMillis()
        when {
            url.value != HOME_URL -> {
                blankSince = 0L
                instrumentation.runOnMainSync { load(HOME_URL) }
                Thread.sleep(300)
            }
            progress.value != 100 -> blankSince = 0L
            blankSince == 0L -> blankSince = now
            now - blankSince >= 1_000 -> return
        }
        Thread.sleep(100)
    }
    error("The tab never settled on a blank page: it shows ${url.value}")
}
