package dev.agentbrowser.passwords

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Saved logins, shown only after the device's screen lock is confirmed. Screenshots are blocked while open. */
@Composable
fun PasswordsScreen(repo: LoginRepository, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val keyguard = remember { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    var unlocked by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf<Pair<Login, String>?>(null) }
    val logins by repo.all.collectAsState()

    val confirm = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) unlocked = true else onBack()
    }

    DisposableEffect(Unit) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    LaunchedEffect(Unit) {
        @Suppress("DEPRECATION")
        val intent = keyguard.createConfirmDeviceCredentialIntent("저장된 비밀번호", "본인 확인 후 볼 수 있어요.")
        // A device without a screen lock has nothing to confirm with.
        if (intent == null) unlocked = true else confirm.launch(intent)
    }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 뒤로") }
            Text("저장된 비밀번호", style = MaterialTheme.typography.titleLarge)
        }
        if (!unlocked) return@Column
        if (logins.isEmpty()) {
            Text("저장된 비밀번호가 없어요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn {
            items(logins, key = { it.id }) { login ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(login.origin)
                    Text(login.username.ifEmpty { "(아이디 없음)" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            scope.launch { repo.reveal(login.id)?.let { revealed = login to it } }
                        }) { Text("보기") }
                        TextButton(onClick = {
                            scope.launch {
                                val password = repo.reveal(login.id) ?: return@launch
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("password", password))
                            }
                        }) { Text("복사") }
                        TextButton(onClick = { scope.launch { repo.delete(login.id) } }) { Text("삭제") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    revealed?.let { (login, password) ->
        AlertDialog(
            onDismissRequest = { revealed = null },
            title = { Text(login.username.ifEmpty { login.origin }) },
            text = { Text(password) },
            confirmButton = { Button(onClick = { revealed = null }) { Text("닫기") } },
        )
    }
}
