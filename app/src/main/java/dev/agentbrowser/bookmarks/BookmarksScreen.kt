package dev.agentbrowser.bookmarks

import dev.agentbrowser.ui.LocalStrings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.agentbrowser.browser.displayHost
import dev.agentbrowser.ui.ScreenScaffold
import kotlinx.coroutines.launch

@Composable
fun BookmarksScreen(
    repo: BookmarkRepository,
    onOpen: (url: String) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
) {
    val bookmarks by repo.all.collectAsState()
    val scope = rememberCoroutineScope()
    val byFolder = bookmarks.groupBy { it.folder }.toSortedMap()
    val colors = MaterialTheme.colorScheme
    val s = LocalStrings.current

    ScreenScaffold(title = s.bookmarks, onBack = onBack) { snackbar ->
        if (bookmarks.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(48.dp))
                Text(s.noBookmarks, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = onImport) { Text(s.importTitle) }
            }
            return@ScreenScaffold
        }
        LazyColumn(Modifier.fillMaxSize()) {
            byFolder.forEach { (folder, entries) ->
                if (folder.isNotEmpty()) {
                    item(key = "folder:$folder") {
                        Text(
                            folder,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                        )
                    }
                }
                items(entries, key = { it.id }) { bookmark ->
                    ListItem(
                        headlineContent = { Text(bookmark.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(displayHost(bookmark.url), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                scope.launch {
                                    repo.delete(bookmark.id)
                                    val result = snackbar.showSnackbar(
                                        message = s.bookmarkDeleted,
                                        actionLabel = s.undo,
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) repo.restore(bookmark)
                                }
                            }) { Icon(Icons.Outlined.Delete, contentDescription = s.delete, tint = colors.onSurfaceVariant) }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onOpen(bookmark.url) },
                    )
                }
            }
        }
    }
}
