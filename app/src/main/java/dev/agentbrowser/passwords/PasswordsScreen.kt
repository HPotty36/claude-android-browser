package dev.agentbrowser.passwords

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.agentbrowser.browser.displayHost
import dev.agentbrowser.ui.ScreenScaffold
import kotlinx.coroutines.launch

/** Saved logins, shown only after the device's screen lock is confirmed. Screenshots are blocked while open. */
@Composable
fun PasswordsScreen(repo: LoginRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val keyguard = remember { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    var unlocked by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf<Pair<Login, String>?>(null) }
    var deleting by remember { mutableStateOf<Login?>(null) }
    val logins by repo.all.collectAsState()
    val colors = MaterialTheme.colorScheme

    val confirm = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) unlocked = true else onBack()
    }

    DisposableEffect(Unit) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    LaunchedEffect(Unit) {
        @Suppress("DEPRECATION")
        val intent = keyguard.createConfirmDeviceCredentialIntent("저장된 비밀번호", "화면 잠금으로 본인 확인을 해 주세요.")
        // A device without a screen lock has nothing to confirm with.
        if (intent == null) unlocked = true else confirm.launch(intent)
    }

    ScreenScaffold(title = "저장된 비밀번호", onBack = onBack) { snackbar ->
        if (!unlocked) return@ScreenScaffold
        if (logins.isEmpty()) {
            Text("저장된 비밀번호가 없어요.", color = colors.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            return@ScreenScaffold
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(logins, key = { it.id }) { login ->
                ListItem(
                    headlineContent = { Text(displayHost(login.origin)) },
                    supportingContent = { Text(login.username.ifEmpty { "아이디 없음" }) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = {
                                scope.launch { repo.reveal(login.id)?.let { revealed = login to it } }
                            }) { Icon(Icons.Outlined.Visibility, contentDescription = "비밀번호 보기", tint = colors.onSurfaceVariant) }
                            IconButton(onClick = {
                                scope.launch {
                                    val password = repo.reveal(login.id) ?: return@launch
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("password", password))
                                    // Android 13 and later show their own "copied" notice.
                                    if (Build.VERSION.SDK_INT < 33) snackbar.showSnackbar("복사했어요")
                                }
                            }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "비밀번호 복사", tint = colors.onSurfaceVariant) }
                            IconButton(onClick = { deleting = login }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "삭제", tint = colors.onSurfaceVariant)
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    revealed?.let { (login, password) ->
        AlertDialog(
            onDismissRequest = { revealed = null },
            title = { Text(login.username.ifEmpty { displayHost(login.origin) }) },
            text = { Text(password, fontFamily = FontFamily.Monospace) },
            confirmButton = { TextButton(onClick = { revealed = null }) { Text("확인") } },
        )
    }
    deleting?.let { login ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("이 비밀번호를 삭제할까요?") },
            text = { Text("${displayHost(login.origin)} · ${login.username.ifEmpty { "아이디 없음" }}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repo.delete(login.id) }
                        deleting = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                ) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } },
        )
    }
}
