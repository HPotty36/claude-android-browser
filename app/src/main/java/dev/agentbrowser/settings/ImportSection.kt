package dev.agentbrowser.settings

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

private fun imported(what: String, added: Int, skipped: Int) =
    "$what ${added}개를 가져왔어요" + if (skipped > 0) " (${skipped}개 건너뜀)" else ""

/** The "bring it over from Chrome" part of the settings screen. */
@Composable
fun ImportSection(
    bookmarks: BookmarkRepository,
    logins: LoginRepository,
    snackbar: SnackbarHostState,
    onShowPasswords: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var help by remember { mutableStateOf<String?>(null) }
    var remindToDeleteCsv by remember { mutableStateOf(false) }
    val rowColors = ListItemDefaults.colors(containerColor = Color.Transparent)

    val pickBookmarks = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val parsed = readText(context, uri)?.let { withContext(Dispatchers.Default) { parseBookmarkHtml(it) } }.orEmpty()
            if (parsed.isEmpty()) {
                snackbar.showSnackbar("북마크가 없는 파일이에요. Chrome에서 내보낸 HTML 파일을 골라 주세요.")
            } else {
                val result = bookmarks.import(parsed)
                snackbar.showSnackbar(imported("북마크", result.added, result.skipped))
            }
        }
    }

    val pickPasswords = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val parsed = readText(context, uri)?.let { withContext(Dispatchers.Default) { parsePasswordCsv(it) } }
            if (parsed == null || (parsed.logins.isEmpty() && parsed.skipped == 0)) {
                snackbar.showSnackbar("Chrome에서 내보낸 비밀번호 CSV 파일이 아니에요.")
            } else {
                val result = logins.import(parsed.logins)
                remindToDeleteCsv = true
                snackbar.showSnackbar(imported("비밀번호", result.added, result.skipped + parsed.skipped))
            }
        }
    }

    SectionLabel("Chrome에서 가져오기")
    ListItem(
        headlineContent = { Text("북마크 가져오기") },
        supportingContent = { Text("Chrome에서 내보낸 HTML 파일") },
        leadingContent = { Icon(Icons.Outlined.Bookmarks, contentDescription = null) },
        trailingContent = {
            IconButton(onClick = {
                help = "PC Chrome의 북마크 관리자에서 ⋮ > 북마크 내보내기로 만든 파일을 이 기기로 옮기세요."
            }) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "내보내는 방법") }
        },
        colors = rowColors,
        modifier = Modifier.clickable { pickBookmarks.launch(arrayOf("text/html", "application/xhtml+xml", "*/*")) },
    )
    ListItem(
        headlineContent = { Text("비밀번호 가져오기") },
        supportingContent = { Text("Chrome에서 내보낸 CSV 파일") },
        leadingContent = { Icon(Icons.Outlined.Key, contentDescription = null) },
        trailingContent = {
            IconButton(onClick = {
                help = "Chrome 설정 > Google 비밀번호 관리자 > 설정 > 비밀번호 내보내기"
            }) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "내보내는 방법") }
        },
        colors = rowColors,
        modifier = Modifier.clickable { pickPasswords.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
    )
    ListItem(
        headlineContent = { Text("저장된 비밀번호") },
        leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = rowColors,
        modifier = Modifier.clickable(onClick = onShowPasswords),
    )

    help?.let { text ->
        AlertDialog(
            onDismissRequest = { help = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { help = null }) { Text("확인") } },
        )
    }
    if (remindToDeleteCsv) {
        AlertDialog(
            onDismissRequest = { remindToDeleteCsv = false },
            title = { Text("CSV 파일을 삭제하세요") },
            text = { Text("이 파일에는 비밀번호가 암호화되지 않은 채 들어 있어요.") },
            confirmButton = { TextButton(onClick = { remindToDeleteCsv = false }) { Text("확인") } },
        )
    }
}
