package dev.agentbrowser.browser

import android.content.Context
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
        load(HOME_URL)
    }

    fun load(url: String) = session.loadUri(url)
    fun back() = session.goBack()
    fun forward() = session.goForward()
    fun reload() = session.reload()
}
