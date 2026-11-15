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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.agentbrowser.bookmarks.BookmarksScreen
import dev.agentbrowser.browser.BrowserScreen
import dev.agentbrowser.passwords.PasswordsScreen
import dev.agentbrowser.settings.ImportSection
import dev.agentbrowser.settings.SettingsScreen
import dev.agentbrowser.ui.AppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        if (savedInstanceState == null) openLink(intent)
        setContent {
            var screen by rememberSaveable { mutableStateOf("browser") }
            val scope = rememberCoroutineScope()
            AppTheme {
                // The surface fills the window so the system bars take the chrome's colour.
                val chrome = if (screen == "browser") MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
                Surface(Modifier.fillMaxSize(), color = chrome) {
                    Box(Modifier.safeDrawingPadding()) {
                        when (screen) {
                            "settings" -> {
                                BackHandler { screen = "browser" }
                                SettingsScreen(app.settings, onBack = { screen = "browser" }) { snackbar ->
                                    ImportSection(app.bookmarks, app.logins, snackbar, onShowPasswords = { screen = "passwords" })
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
                                val saved = bookmarks.any { it.url == url }
                                BrowserScreen(
                                    controller = app.browser,
                                    chat = app.chat,
                                    loginPrompts = app.loginPrompts,
                                    isBookmarked = saved,
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openLink(intent)
    }

    /** Opens an http(s) link another app handed over. */
    private fun openLink(intent: Intent?) {
        val link = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        if (link.startsWith("http://", ignoreCase = true) || link.startsWith("https://", ignoreCase = true)) {
            (application as App).browser.load(link)
        }
    }
}
