package dev.agentbrowser.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.agentbrowser.chat.ChatPanel
import dev.agentbrowser.chat.ChatViewModel
import dev.agentbrowser.page.await
import dev.agentbrowser.passwords.LoginPromptDelegate
import dev.agentbrowser.passwords.LoginPromptDialog
import org.mozilla.geckoview.GeckoView

@Composable
fun BrowserScreen(
    controller: BrowserController,
    chat: ChatViewModel,
    loginPrompts: LoginPromptDelegate,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val url by controller.url.collectAsState()
    val canGoBack by controller.canGoBack.collectAsState()
    val canGoForward by controller.canGoForward.collectAsState()
    var address by remember { mutableStateOf(url) }
    var chatOpen by rememberSaveable { mutableStateOf(false) }
    val agentRunning by chat.running.collectAsState()
    val loginPrompt by loginPrompts.prompt.collectAsState()
    loginPrompt?.let { LoginPromptDialog(it) }
    val focus = LocalFocusManager.current

    LaunchedEffect(url) { address = url }
    BackHandler(enabled = canGoBack) { controller.back() }
    BackHandler(enabled = chatOpen) { chatOpen = false }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                singleLine = true,
                placeholder = { Text("주소 또는 검색어") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    if (address.isNotBlank()) controller.load(toUrl(address))
                    focus.clearFocus()
                }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { controller.reload() }) { Text("새로고침") }
        }
        AndroidView(
            factory = { context ->
                GeckoView(context).apply {
                    setSession(controller.session)
                    controller.capturePage = { capturePixels().await() }
                }
            },
            onRelease = { view ->
                controller.capturePage = null
                view.releaseSession()
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        if (chatOpen) {
            ChatPanel(chat, onClose = { chatOpen = false }, onOpenSettings = onSettings, modifier = Modifier.weight(1f))
        } else if (agentRunning) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Claude 작업 중", modifier = Modifier.weight(1f))
                TextButton(onClick = { chatOpen = true }) { Text("보기") }
                Button(onClick = { chat.stop() }) { Text("정지") }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { controller.back() }, enabled = canGoBack) { Text("뒤로") }
            TextButton(onClick = { controller.forward() }, enabled = canGoForward) { Text("앞으로") }
            TextButton(onClick = onBookmarks) { Text("북마크") }
            Button(onClick = { chatOpen = !chatOpen }) { Text("Claude") }
            TextButton(onClick = onSettings) { Text("설정") }
        }
    }
}
