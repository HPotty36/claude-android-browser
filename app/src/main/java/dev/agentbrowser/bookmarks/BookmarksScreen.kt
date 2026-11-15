package dev.agentbrowser.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun BookmarksScreen(
    repo: BookmarkRepository,
    currentTitle: String,
    currentUrl: String,
    onOpen: (url: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bookmarks by repo.all.collectAsState()
    val scope = rememberCoroutineScope()
    val alreadySaved = bookmarks.any { it.url == currentUrl }
    val byFolder = bookmarks.groupBy { it.folder }.toSortedMap()

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 뒤로") }
            Text("북마크", style = MaterialTheme.typography.titleLarge)
        }
        Button(
            onClick = { scope.launch { repo.add(currentTitle, currentUrl) } },
            enabled = currentUrl.startsWith("http") && !alreadySaved,
        ) { Text(if (alreadySaved) "현재 페이지는 저장돼 있어요" else "현재 페이지 추가") }

        if (bookmarks.isEmpty()) {
            Text(
                "북마크가 없어요. 설정에서 Chrome 북마크를 가져올 수 있어요.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            byFolder.forEach { (folder, entries) ->
                if (folder.isNotEmpty()) {
                    item(key = "folder:$folder") {
                        Text(
                            folder,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                }
                items(entries, key = { it.id }) { bookmark ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { onOpen(bookmark.url) }.padding(vertical = 8.dp)) {
                            Text(bookmark.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                bookmark.url,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { scope.launch { repo.delete(bookmark.id) } }) { Text("삭제") }
                    }
                }
            }
        }
    }
}
