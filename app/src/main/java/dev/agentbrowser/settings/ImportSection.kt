package dev.agentbrowser.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.agentbrowser.bookmarks.BookmarkRepository
import dev.agentbrowser.bookmarks.parseBookmarkHtml
import dev.agentbrowser.passwords.LoginRepository
import dev.agentbrowser.passwords.parsePasswordCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private suspend fun readText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
}

/** The "bring it over from Chrome" part of the settings screen. */
@Composable
fun ImportSection(bookmarks: BookmarkRepository, logins: LoginRepository, onShowPasswords: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var remindToDeleteCsv by remember { mutableStateOf(false) }

    val pickBookmarks = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val parsed = readText(context, uri)?.let { withContext(Dispatchers.Default) { parseBookmarkHtml(it) } }.orEmpty()
            message = if (parsed.isEmpty()) {
                "북마크를 찾지 못했어요. Chrome에서 내보낸 HTML 파일인지 확인하세요."
            } else {
                val result = bookmarks.import(parsed)
                "북마크 ${result.added}개를 가져왔어요. ${result.skipped}개는 건너뛰었어요."
            }
        }
    }

    val pickPasswords = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val parsed = readText(context, uri)?.let { withContext(Dispatchers.Default) { parsePasswordCsv(it) } }
            if (parsed == null || (parsed.logins.isEmpty() && parsed.skipped == 0)) {
                message = "Chrome에서 내보낸 비밀번호 CSV 파일이 아니에요."
            } else {
                val result = logins.import(parsed.logins)
                message = "비밀번호 ${result.added}개를 가져왔어요. ${result.skipped + parsed.skipped}개는 건너뛰었어요."
                remindToDeleteCsv = true
            }
        }
    }

    HorizontalDivider()
    Text("Chrome에서 가져오기", style = MaterialTheme.typography.titleMedium)
    Text(
        "북마크: PC Chrome → 북마크 관리자 → 북마크 내보내기로 만든 HTML 파일을 폰으로 옮겨 고르세요.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = { pickBookmarks.launch(arrayOf("text/html", "application/xhtml+xml", "*/*")) }) {
        Text("북마크 가져오기")
    }
    Text(
        "비밀번호: Chrome → 설정 → Google 비밀번호 관리자 → 설정 → 비밀번호 내보내기로 만든 CSV 파일을 고르세요.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = { pickPasswords.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) }) {
        Text("비밀번호 가져오기")
    }
    OutlinedButton(onClick = onShowPasswords) { Text("저장된 비밀번호") }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("확인") } },
        )
    }
    if (message == null && remindToDeleteCsv) {
        AlertDialog(
            onDismissRequest = { remindToDeleteCsv = false },
            title = { Text("원본 파일을 지우세요") },
            text = { Text("원본 CSV 파일에는 비밀번호가 그대로 적혀 있어요. 지금 파일을 지우세요.") },
            confirmButton = { TextButton(onClick = { remindToDeleteCsv = false }) { Text("확인") } },
        )
    }
}
