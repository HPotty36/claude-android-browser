package dev.agentbrowser.browser

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dev.agentbrowser.page.CurrentTabDriver
import dev.agentbrowser.page.GeckoPageDriver
import dev.agentbrowser.page.PageDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** The blank page the start screen is drawn over. */
const val HOME_URL = "about:blank"

/** Where site addresses are looked up when encrypted DNS is on. */
const val DOH_URL = "https://cloudflare-dns.com/dns-query"

/** uBlock Origin, bundled as assets/ublock by the build. */
const val AD_BLOCKER_ID = "uBlock0@raymondhill.net"

// "First" falls back to the network's own DNS when the encrypted resolver cannot be reached, so pages still load.
private fun trrMode(on: Boolean) =
    if (on) GeckoRuntimeSettings.TRR_MODE_FIRST else GeckoRuntimeSettings.TRR_MODE_OFF

/** One open page: its engine session and what the chrome shows about it. */
class Tab internal constructor(val id: Long, val session: GeckoSession, capture: suspend () -> Bitmap?) {
    internal val _url = MutableStateFlow("")
    internal val _title = MutableStateFlow("")
    internal val _progress = MutableStateFlow(100)
    internal val _canGoBack = MutableStateFlow(false)
    internal val _canGoForward = MutableStateFlow(false)
    val url: StateFlow<String> = _url
    val title: StateFlow<String> = _title
    /** Page load progress, 0..100; 100 when idle. */
    val progress: StateFlow<Int> = _progress
    val canGoBack: StateFlow<Boolean> = _canGoBack
    val canGoForward: StateFlow<Boolean> = _canGoForward

    internal val driver = GeckoPageDriver(session, capture)
    /** A load asked for before the agent extension was ready. */
    internal var pendingUrl: String? = null
}

/** Owns the engine and the open tabs. Everything that acts on "the page" acts on the current tab. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserController(
    context: Context,
    scope: CoroutineScope = MainScope(),
    encryptedDns: Boolean = true,
    adBlock: Boolean = true,
) {
    // With encrypted DNS on, the engine also uses ECH on sites that publish a key for it.
    val runtime: GeckoRuntime = GeckoRuntime.create(
        context.applicationContext,
        GeckoRuntimeSettings.Builder()
            .trustedRecursiveResolverUri(DOH_URL)
            .trustedRecursiveResolverMode(trrMode(encryptedDns))
            .build(),
    )

    /** Set by the screen that shows the page; returns what is visible, or null when nothing is attached. */
    var capturePage: (suspend () -> Bitmap?)? = null

    /** Applied to every tab, present and future. */
    var promptDelegate: GeckoSession.PromptDelegate? = null
        set(value) {
            field = value
            _tabs.value.forEach { it.session.promptDelegate = value }
        }

    private var nextId = 1L
    private var extension: WebExtension? = null
    private var extensionReady = false

    private var adBlocker: WebExtension? = null
    private var adBlockWanted = adBlock
    private val _adBlockActive = MutableStateFlow<Boolean?>(null)
    /** Whether the ad blocker is running; null until the engine has installed it. */
    val adBlockActive: StateFlow<Boolean?> = _adBlockActive

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs

    private val _current = MutableStateFlow(addTab(GeckoSession(), HOME_URL))
    val current: StateFlow<Tab> = _current

    val url: StateFlow<String> = current.flatMapLatest { it.url }.stateIn(scope, SharingStarted.Eagerly, "")
    val title: StateFlow<String> = current.flatMapLatest { it.title }.stateIn(scope, SharingStarted.Eagerly, "")
    val progress: StateFlow<Int> = current.flatMapLatest { it.progress }.stateIn(scope, SharingStarted.Eagerly, 100)
    val canGoBack: StateFlow<Boolean> = current.flatMapLatest { it.canGoBack }.stateIn(scope, SharingStarted.Eagerly, false)
    val canGoForward: StateFlow<Boolean> =
        current.flatMapLatest { it.canGoForward }.stateIn(scope, SharingStarted.Eagerly, false)

    val pageDriver: PageDriver = CurrentTabDriver { _current.value.driver }

    init {
        runtime.webExtensionController
            .ensureBuiltIn("resource://android/assets/agent/", "agent@agentbrowser")
            .accept(
                { installed -> onExtensionReady(installed) },
                { error ->
                    Log.e("AgentBrowser", "agent extension failed to install", error)
                    onExtensionReady(null)
                },
            )
    }

    init {
        runtime.webExtensionController
            .ensureBuiltIn("resource://android/assets/ublock/", AD_BLOCKER_ID)
            .accept(
                { installed -> onAdBlocker(installed) },
                { error -> Log.e("AgentBrowser", "ad blocker failed to install", error) },
            )
    }

    private fun onAdBlocker(installed: WebExtension?) {
        adBlocker = installed ?: return
        _adBlockActive.value = installed.metaData.enabled
        applyAdBlock()
    }

    private fun applyAdBlock() {
        val blocker = adBlocker ?: return
        if (blocker.metaData.enabled == adBlockWanted) return
        val extensions = runtime.webExtensionController
        val source = WebExtensionController.EnableSource.USER
        val change = if (adBlockWanted) extensions.enable(blocker, source) else extensions.disable(blocker, source)
        change.accept(
            { changed -> onAdBlocker(changed) },
            { error -> Log.e("AgentBrowser", "ad blocker could not be switched", error) },
        )
    }

    fun setAdBlock(on: Boolean) {
        adBlockWanted = on
        applyAdBlock()
    }

    /** Whether site addresses are looked up through [DOH_URL]. The engine does not report this back, so it is kept here. */
    var encryptedDns: Boolean = encryptedDns
        private set

    fun setEncryptedDns(on: Boolean) {
        encryptedDns = on
        runtime.settings.setTrustedRecursiveResolverMode(trrMode(on))
    }

    /** Pages load only after the extension is ready, so their content script can reach the app. */
    private fun onExtensionReady(installed: WebExtension?) {
        extension = installed
        extensionReady = true
        _tabs.value.forEach { tab ->
            attachDriver(tab)
            tab.pendingUrl?.let { tab.session.loadUri(it) }
            tab.pendingUrl = null
        }
    }

    private fun attachDriver(tab: Tab) {
        extension?.let { tab.session.webExtensionController.setMessageDelegate(it, tab.driver, "browser") }
    }

    /**
     * Adds a tab around [session]. With a [url] the session is opened here and loads it; without one the
     * engine opens the session itself (a page asked for a new window).
     */
    private fun addTab(session: GeckoSession, url: String?): Tab {
        val tab = Tab(nextId++, session) { capturePage?.invoke() }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                tab._url.value = url.orEmpty()
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab._canGoBack.value = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab._canGoForward.value = canGoForward
            }

            // A link that wants its own window gets its own tab.
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val child = GeckoSession()
                _current.value = addTab(child, url = null)
                return GeckoResult.fromValue(child)
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                tab._progress.value = progress
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab._progress.value = 100
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab._title.value = title.orEmpty()
            }
        }
        session.promptDelegate = promptDelegate
        attachDriver(tab)
        if (url != null) {
            session.open(runtime)
            if (extensionReady) session.loadUri(url) else tab.pendingUrl = url
        }
        _tabs.value = _tabs.value + tab
        return tab
    }

    /** Opens [url] in a new tab and switches to it. */
    fun newTab(url: String = HOME_URL): Tab = addTab(GeckoSession(), url).also { _current.value = it }

    fun select(id: Long) {
        _tabs.value.firstOrNull { it.id == id }?.let { _current.value = it }
    }

    /** Closes a tab. Closing the last one leaves a fresh blank tab. */
    fun close(id: Long) {
        val closing = _tabs.value.firstOrNull { it.id == id } ?: return
        val next = currentAfterClose(_tabs.value.map { it.id }, _current.value.id, id)
        _tabs.value = _tabs.value - closing
        if (next == null) newTab() else select(next)
        closing.session.close()
    }

    /** A load requested before the extension is ready waits for it instead of racing it. */
    fun load(url: String) {
        val tab = _current.value
        if (extensionReady) tab.session.loadUri(url) else tab.pendingUrl = url
    }

    fun back() = _current.value.session.goBack()
    fun forward() = _current.value.session.goForward()
    fun home() = load(HOME_URL)
    fun reload() = _current.value.session.reload()
    fun stop() = _current.value.session.stop()
}
