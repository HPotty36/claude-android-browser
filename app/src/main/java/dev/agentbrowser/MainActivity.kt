package dev.agentbrowser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.agentbrowser.bookmarks.BookmarksScreen
import dev.agentbrowser.browser.BrowserScreen
import dev.agentbrowser.browser.HOME_URL
import dev.agentbrowser.browser.Shortcut
import dev.agentbrowser.passwords.PasswordsScreen
import dev.agentbrowser.settings.ImportSection
import android.view.KeyEvent
import dev.agentbrowser.browser.shortcutFor
import dev.agentbrowser.browser.wantsDesktopSite
import dev.agentbrowser.settings.SettingsScreen
import dev.agentbrowser.ui.AppTheme
import dev.agentbrowser.ui.LocalStrings
import dev.agentbrowser.ui.stringsFor
import dev.agentbrowser.update.UpdateSection
import dev.agentbrowser.update.hasUpdate
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        if (savedInstanceState == null) {
            openLink(intent)
            app.scope.launch { app.updater.checkIfDue(System.currentTimeMillis()) }
        }
        setContent {
            // The shorter side of the screen decides, so a tablet, an unfolded foldable or an external display
            // gets desktop pages and a phone held sideways does not.
            val desktopChoice by app.settings.desktopSite.collectAsState()
            val desktopSite = wantsDesktopSite(desktopChoice, LocalConfiguration.current.smallestScreenWidthDp)
            LaunchedEffect(desktopSite) { app.browser.setDesktopSite(desktopSite) }
            var screen by rememberSaveable { mutableStateOf("browser") }
            val scope = rememberCoroutineScope()
            val language by app.settings.language.collectAsState()
            val strings = stringsFor(language, Locale.getDefault().language)
            AppTheme {
              CompositionLocalProvider(LocalStrings provides strings) {
                // The surface fills the window so the system bars take the chrome's colour.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.safeDrawingPadding()) {
                        when (screen) {
                            "settings" -> {
                                BackHandler { screen = "browser" }
                                SettingsScreen(app.settings, onBack = { screen = "browser" }) { snackbar ->
                                    ImportSection(app.bookmarks, app.logins, snackbar, onShowPasswords = { screen = "passwords" })
                                    UpdateSection(app.updater, BuildConfig.VERSION_NAME, app.scope)
                                }
                            }
                            "passwords" -> {
                                BackHandler { screen = "settings" }
                                PasswordsScreen(app.logins, onBack = { screen = "settings" })
                            }
                            "bookmarks" -> {
                                BackHandler { screen = "browser" }
                                BookmarksScreen(
                                    repo = app.bookmarks,
                                    onOpen = {
                                        app.browser.load(it)
                                        screen = "browser"
                                    },
                                    onImport = { screen = "settings" },
                                    onBack = { screen = "browser" },
                                )
                            }
                            else -> {
                                val url by app.browser.url.collectAsState()
                                val title by app.browser.title.collectAsState()
                                val bookmarks by app.bookmarks.all.collectAsState()
                                val searchEngine by app.settings.searchEngine.collectAsState()
                                val saved = bookmarks.any { it.url == url }
                                val updateState by app.updater.state.collectAsState()
                                BrowserScreen(
                                    controller = app.browser,
                                    chat = app.chat,
                                    loginPrompts = app.loginPrompts,
                                    shortcuts = bookmarks.map { Shortcut(it.title, it.url) },
                                    searchUrl = searchEngine.queryUrl,
                                    isBookmarked = saved,
                                    updateAvailable = updateState.hasUpdate,
                                    shortcutEvents = app.shortcuts,
                                    onToggleBookmark = {
                                        scope.launch {
                                            if (saved) app.bookmarks.deleteByUrl(url) else app.bookmarks.add(title, url)
                                        }
                                    },
                                    onBookmarks = { screen = "bookmarks" },
                                    onSettings = { screen = "settings" },
                                )
                            }
                        }
                    }
                }
              }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openLink(intent)
    }

    /** Opens an http(s) link another app handed over. */
    // Shortcuts are taken here, ahead of the page, so they work while the page has the keyboard.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val shortcuts = (application as App).shortcuts
        if (event.action == KeyEvent.ACTION_DOWN && shortcuts.subscriptionCount.value > 0) {
            val shortcut = shortcutFor(event.keyCode, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed)
            if (shortcut != null) {
                if (event.repeatCount == 0) shortcuts.tryEmit(shortcut)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun openLink(intent: Intent?) {
        val link = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        if (link.startsWith("http://", ignoreCase = true) || link.startsWith("https://", ignoreCase = true)) {
            val browser = (application as App).browser
            // A link from another app gets its own tab unless the current one is still blank.
            val blank = browser.url.value.let { it.isEmpty() || it == HOME_URL }
            if (blank) browser.load(link) else browser.newTab(link)
        }
    }
}
