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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.agentbrowser.browser.BrowserScreen
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
                            SettingsScreen(app.settings, onBack = { screen = "browser" })
                        }
                        else -> BrowserScreen(
                            controller = app.browser,
                            chat = app.chat,
                            onBookmarks = {},
                            onSettings = { screen = "settings" },
                        )
                    }
                }
            }
        }
    }
}
