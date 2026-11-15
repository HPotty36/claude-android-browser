package dev.agentbrowser.settings

import dev.agentbrowser.ui.LocalStrings
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.agentbrowser.bookmarks.BookmarkRepository
import dev.agentbrowser.bookmarks.parseBookmarkHtml
import dev.agentbrowser.passwords.LoginRepository
import dev.agentbrowser.passwords.parsePasswordCsv
import dev.agentbrowser.ui.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private suspend fun readText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
}

/** The "bring it over from Chrome" part of the settings screen. */
@Composable
fun ImportSection(
    bookmarks: BookmarkRepository,
    logins: LoginRepository,
    snackbar: SnackbarHostState,
    onShowPasswords: () -> Unit,
) {
    val context = LocalContext.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    var help by remember { mutableStateOf<String?>(null) }
    var remindToDeleteCsv by remember { mutableStateOf(false) }
    val rowColors = ListItemDefaults.colors(containerColor = Color.Transparent)

    val pickBookmarks = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val parsed = readText(context, uri)?.let { withContext(Dispatchers.Default) { parseBookmarkHtml(it) } }.orEmpty()
            if (parsed.isEmpty()) {
                snackbar.showSnackbar(s.noBookmarksInFile)
            } else {
                val result = bookmarks.import(parsed)
                snackbar.showSnackbar(s.importedBookmarks(result.added, result.skipped))
            }
        }
    }

    val pickPasswords = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val parsed = readText(context, uri)?.let { withContext(Dispatchers.Default) { parsePasswordCsv(it) } }
            if (parsed == null || (parsed.logins.isEmpty() && parsed.skipped == 0)) {
                snackbar.showSnackbar(s.notAPasswordCsv)
            } else {
                val result = logins.import(parsed.logins)
                remindToDeleteCsv = true
                snackbar.showSnackbar(s.importedPasswords(result.added, result.skipped + parsed.skipped))
            }
        }
    }

    SectionLabel(s.importTitle)
    ListItem(
        headlineContent = { Text(s.importBookmarks) },
        supportingContent = { Text(s.importBookmarksNote) },
        leadingContent = { Icon(Icons.Outlined.Bookmarks, contentDescription = null) },
        trailingContent = {
            IconButton(onClick = {
                help = s.importBookmarksHelp
            }) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = s.howToExport) }
        },
        colors = rowColors,
        modifier = Modifier.clickable { pickBookmarks.launch(arrayOf("text/html", "application/xhtml+xml", "*/*")) },
    )
    ListItem(
        headlineContent = { Text(s.importPasswords) },
        supportingContent = { Text(s.importPasswordsNote) },
        leadingContent = { Icon(Icons.Outlined.Key, contentDescription = null) },
        trailingContent = {
            IconButton(onClick = {
                help = s.importPasswordsHelp
            }) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = s.howToExport) }
        },
        colors = rowColors,
        modifier = Modifier.clickable { pickPasswords.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
    )
    ListItem(
        headlineContent = { Text(s.savedPasswords) },
        leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = rowColors,
        modifier = Modifier.clickable(onClick = onShowPasswords),
    )

    help?.let { text ->
        AlertDialog(
            onDismissRequest = { help = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { help = null }) { Text(s.ok) } },
        )
    }
    if (remindToDeleteCsv) {
        AlertDialog(
            onDismissRequest = { remindToDeleteCsv = false },
            title = { Text(s.deleteCsvTitle) },
            text = { Text(s.deleteCsvBody) },
            confirmButton = { TextButton(onClick = { remindToDeleteCsv = false }) { Text(s.ok) } },
        )
    }
}
