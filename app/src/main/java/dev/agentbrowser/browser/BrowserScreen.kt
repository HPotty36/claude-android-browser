package dev.agentbrowser.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.agentbrowser.chat.ChatPanel
import dev.agentbrowser.chat.ChatViewModel
import dev.agentbrowser.chat.ConfirmBar
import dev.agentbrowser.page.await
import dev.agentbrowser.passwords.LoginPromptDelegate
import dev.agentbrowser.passwords.LoginPromptDialog
import dev.agentbrowser.ui.PillField
import org.mozilla.geckoview.GeckoView

@Composable
fun BrowserScreen(
    controller: BrowserController,
    chat: ChatViewModel,
    loginPrompts: LoginPromptDelegate,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canGoBack by controller.canGoBack.collectAsState()
    val canGoForward by controller.canGoForward.collectAsState()
    var chatOpen by rememberSaveable { mutableStateOf(false) }
    val agentRunning by chat.running.collectAsState()
    val pendingConfirm by chat.pendingConfirm.collectAsState()
    val loginPrompt by loginPrompts.prompt.collectAsState()
    loginPrompt?.let { LoginPromptDialog(it) }

    BackHandler(enabled = canGoBack) { controller.back() }
    BackHandler(enabled = chatOpen) { chatOpen = false }

    Column(modifier.fillMaxSize()) {
        AddressBar(controller, isBookmarked, onToggleBookmark)
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
        // Always on screen while Claude waits for an answer, whether or not the chat panel is open.
        pendingConfirm?.let { description ->
            ConfirmBar(description, onAnswer = chat::answerConfirm)
        }
        if (chatOpen) {
            ChatPanel(chat, onClose = { chatOpen = false }, onOpenSettings = onSettings, modifier = Modifier.weight(1f))
        } else if (agentRunning) {
            RunningStrip(onOpen = { chatOpen = true }, onStop = chat::stop)
        }
        BottomBar(
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            chatOpen = chatOpen,
            onBack = controller::back,
            onForward = controller::forward,
            onClaude = { chatOpen = !chatOpen },
            onBookmarks = onBookmarks,
            onSettings = onSettings,
        )
    }
}

@Composable
private fun AddressBar(controller: BrowserController, isBookmarked: Boolean, onToggleBookmark: () -> Unit) {
    val url by controller.url.collectAsState()
    val progress by controller.progress.collectAsState()
    val loading = progress in 1..99
    // Editing swaps the read-only host for a fresh text field, so the field always starts from the full URL.
    var editing by remember { mutableStateOf<TextFieldValue?>(null) }
    val focus = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme

    BackHandler(enabled = editing != null) { editing = null }

    Surface(color = colors.surfaceContainer) {
        Box {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val draft = editing
                if (draft == null) {
                    Row(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.surfaceContainerHigh)
                            .clickable(onClickLabel = "주소 편집") { editing = TextFieldValue(url, TextRange(0, url.length)) }
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (url.startsWith("https://")) Icons.Outlined.Lock else Icons.Outlined.Public,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            displayHost(url).ifEmpty { "검색어 또는 주소 입력" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (url.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (loading) {
                            IconButton(onClick = controller::stop) {
                                Icon(Icons.Filled.Close, contentDescription = "로딩 중지", tint = colors.onSurfaceVariant)
                            }
                        } else {
                            IconButton(onClick = controller::reload) {
                                Icon(Icons.Filled.Refresh, contentDescription = "새로고침", tint = colors.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    val requester = remember { FocusRequester() }
                    var hadFocus by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { requester.requestFocus() }
                    PillField(
                        value = draft,
                        onValueChange = { editing = it },
                        placeholder = "검색어 또는 주소 입력",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            if (draft.text.isNotBlank()) controller.load(toUrl(draft.text))
                            focus.clearFocus()
                            editing = null
                        }),
                        modifier = Modifier.weight(1f).focusRequester(requester).onFocusChanged { state ->
                            if (state.isFocused) hadFocus = true else if (hadFocus) editing = null
                        },
                        trailing = {
                            IconButton(onClick = { editing = TextFieldValue("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "지우기", tint = colors.onSurfaceVariant)
                            }
                        },
                    )
                }
                IconButton(onClick = onToggleBookmark, enabled = url.startsWith("http")) {
                    if (isBookmarked) {
                        Icon(Icons.Filled.Star, contentDescription = "북마크에서 삭제", tint = colors.primary)
                    } else {
                        Icon(Icons.Outlined.StarBorder, contentDescription = "북마크에 추가", tint = colors.onSurfaceVariant)
                    }
                }
            }
            if (loading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    color = colors.primary,
                    trackColor = Color.Transparent,
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp),
                )
            }
        }
    }
}

@Composable
private fun RunningStrip(onOpen: () -> Unit, onStop: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.primaryContainer) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).clickable(onClickLabel = "대화 열기", onClick = onOpen).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                "Claude가 작업 중",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onStop, colors = ButtonDefaults.textButtonColors(contentColor = colors.error)) {
                Text("중지")
            }
        }
    }
}

@Composable
private fun BottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    chatOpen: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onClaude: () -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainer) {
        Column {
            HorizontalDivider(color = colors.outlineVariant)
            Row(
                Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, enabled = canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
                IconButton(onClick = onForward, enabled = canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "앞으로")
                }
                FilledTonalButton(
                    onClick = onClaude,
                    colors = if (chatOpen) {
                        ButtonDefaults.filledTonalButtonColors(containerColor = colors.primary, contentColor = colors.onPrimary)
                    } else {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer,
                        )
                    },
                    border = BorderStroke(0.dp, Color.Transparent),
                    modifier = Modifier.height(36.dp),
                ) { Text("Claude") }
                IconButton(onClick = onBookmarks) {
                    Icon(Icons.Outlined.Bookmarks, contentDescription = "북마크")
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "설정")
                }
            }
        }
    }
}
