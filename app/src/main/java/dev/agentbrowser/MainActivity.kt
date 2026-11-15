package dev.agentbrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.agentbrowser.bookmarks.BookmarksScreen
import dev.agentbrowser.browser.BrowserScreen
import dev.agentbrowser.passwords.PasswordsScreen
import dev.agentbrowser.settings.ImportSection
import dev.agentbrowser.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        setContent {
            var screen by rememberSaveable { mutableStateOf("browser") }
            MaterialTheme {
                Surface(Modifier.safeDrawingPadding().imePadding()) {
                    when (screen) {
                        "settings" -> {
                            BackHandler { screen = "browser" }
                            SettingsScreen(app.settings, onBack = { screen = "browser" }) {
                                ImportSection(app.bookmarks, app.logins, onShowPasswords = { screen = "passwords" })
                            }
                        }
                        "passwords" -> {
                            BackHandler { screen = "settings" }
                            PasswordsScreen(app.logins, onBack = { screen = "settings" })
                        }
                        "bookmarks" -> {
                            BackHandler { screen = "browser" }
                            val title by app.browser.title.collectAsState()
                            val url by app.browser.url.collectAsState()
                            BookmarksScreen(
                                repo = app.bookmarks,
                                currentTitle = title,
                                currentUrl = url,
                                onOpen = {
                                    app.browser.load(it)
                                    screen = "browser"
                                },
                                onBack = { screen = "browser" },
                            )
                        }
                        else -> BrowserScreen(
                            controller = app.browser,
                            chat = app.chat,
                            loginPrompts = app.loginPrompts,
                            onBookmarks = { screen = "bookmarks" },
                            onSettings = { screen = "settings" },
                        )
                    }
                }
            }
        }
    }
}
