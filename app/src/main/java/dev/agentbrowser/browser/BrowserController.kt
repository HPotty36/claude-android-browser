package dev.agentbrowser.browser

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dev.agentbrowser.page.GeckoPageDriver
import dev.agentbrowser.page.PageDriver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

const val HOME_URL = "https://www.google.com"

class BrowserController(context: Context) {
    val runtime: GeckoRuntime = GeckoRuntime.create(context.applicationContext)
    val session: GeckoSession = GeckoSession()

    private val _url = MutableStateFlow("")
    private val _title = MutableStateFlow("")
    private val _canGoBack = MutableStateFlow(false)
    private val _canGoForward = MutableStateFlow(false)
    val url: StateFlow<String> = _url
    val title: StateFlow<String> = _title
    val canGoBack: StateFlow<Boolean> = _canGoBack
    val canGoForward: StateFlow<Boolean> = _canGoForward

    /** Set by the screen that shows the page; returns what is visible, or null when nothing is attached. */
    var capturePage: (suspend () -> Bitmap?)? = null

    private val driver = GeckoPageDriver(session) { capturePage?.invoke() }
    val pageDriver: PageDriver = driver

    private var extensionReady = false
    private var firstUrl = HOME_URL

    init {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                _url.value = url.orEmpty()
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                _canGoBack.value = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                _canGoForward.value = canGoForward
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                _title.value = title.orEmpty()
            }
        }
        session.open(runtime)
        installAgentExtension()
    }

    /** The first page loads only after the extension is ready, so its content script can reach the app. */
    private fun installAgentExtension() {
        runtime.webExtensionController
            .ensureBuiltIn("resource://android/assets/agent/", "agent@agentbrowser")
            .accept(
                { extension ->
                    if (extension != null) {
                        session.webExtensionController.setMessageDelegate(extension, driver, "browser")
                    }
                    startLoading()
                },
                { error ->
                    Log.e("AgentBrowser", "agent extension failed to install", error)
                    startLoading()
                },
            )
    }

    private fun startLoading() {
        extensionReady = true
        session.loadUri(firstUrl)
    }

    /** A load requested before the extension is ready replaces the first page instead of racing it. */
    fun load(url: String) {
        if (extensionReady) session.loadUri(url) else firstUrl = url
    }
    fun back() = session.goBack()
    fun forward() = session.goForward()
    fun reload() = session.reload()
}
