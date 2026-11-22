package dev.agentbrowser.browser

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.agentbrowser.R

data class Shortcut(val title: String, val url: String)

/**
 * What a blank tab shows: the mark at the centre, and the user's own bookmarks under it when there are
 * any. Typing happens in the bar below.
 */
@Composable
fun StartPage(bookmarks: List<Shortcut>, onOpen: (url: String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shortcuts = bookmarks.take(8)
    Surface(color = colors.surface, modifier = modifier.fillMaxSize().clip(RoundedCornerShape(0.dp))) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(if (shortcuts.isEmpty()) 1f else 0.7f))
            Icon(
                painterResource(R.drawable.ic_mark),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(128.dp),
            )
            if (shortcuts.isNotEmpty()) {
                Spacer(Modifier.height(56.dp))
                shortcuts.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { shortcut -> ShortcutTile(shortcut, onOpen, Modifier.weight(1f)) }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }
        }
    }
}

@Composable
private fun ShortcutTile(shortcut: Shortcut, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable { onOpen(shortcut.url) }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).background(colors.surfaceContainerHigh, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                shortcut.title.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            shortcut.title,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp),
        )
    }
}
