package dev.agentbrowser

import android.app.Application
import dev.agentbrowser.browser.BrowserController

class App : Application() {
    /** One GeckoRuntime per process, so the controller lives with the application. */
    val browser: BrowserController by lazy { BrowserController(this) }
}
