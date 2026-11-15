package dev.agentbrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.agentbrowser.browser.BrowserScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val browser = (application as App).browser
        setContent {
            MaterialTheme {
                Surface(Modifier.safeDrawingPadding().imePadding()) {
                    BrowserScreen(
                        controller = browser,
                        onBookmarks = {},
                        onClaude = {},
                        onSettings = {},
                    )
                }
            }
        }
    }
}
