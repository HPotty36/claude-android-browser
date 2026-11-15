package dev.agentbrowser.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.agentbrowser.R
import dev.agentbrowser.chat.ChatPanel
import dev.agentbrowser.chat.ChatViewModel
import dev.agentbrowser.chat.ConfirmBar
import dev.agentbrowser.chat.PANEL_DEFAULT
import dev.agentbrowser.chat.PanelRest
import dev.agentbrowser.chat.dragPanel
import dev.agentbrowser.chat.restPanel
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
    shortcuts: List<Shortcut>,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val url by controller.url.collectAsState()
    val canGoBack by controller.canGoBack.collectAsState()
    var chatOpen by rememberSaveable { mutableStateOf(false) }
    // Expanded, the chat takes the whole screen: the page and the bar step aside until it is shrunk again.
    var chatExpanded by rememberSaveable { mutableStateOf(false) }
    val fullChat = chatOpen && chatExpanded
    // The panel's share of the space above the bar; dragging its top edge changes it.
    var chatFraction by rememberSaveable { mutableStateOf(PANEL_DEFAULT) }
    var areaPx by remember { mutableStateOf(0) }
    val agentRunning by chat.running.collectAsState()
    val pendingConfirm by chat.pendingConfirm.collectAsState()
    val loginPrompt by loginPrompts.prompt.collectAsState()
    loginPrompt?.let { LoginPromptDialog(it) }
    val onStartPage = url.isEmpty() || url == HOME_URL
    val colors = MaterialTheme.colorScheme

    BackHandler(enabled = canGoBack) { controller.back() }
    BackHandler(enabled = chatOpen) { chatOpen = false }
    BackHandler(enabled = fullChat) { chatExpanded = false }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().onSizeChanged { areaPx = it.height }) {
            // While the panel is open the page keeps a sliver at least, so its view never loses its session.
            val pageWeight = if (chatOpen) (1f - chatFraction).coerceAtLeast(0.05f) else 1f
            Box(if (fullChat) Modifier.fillMaxWidth().height(0.dp) else Modifier.weight(pageWeight).fillMaxWidth()) {
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
                    modifier = Modifier.fillMaxSize(),
                )
                if (onStartPage) StartPage(shortcuts, onOpen = controller::load)
                // The page wears the accent while Claude has the controls.
                if (agentRunning) Box(Modifier.fillMaxSize().border(2.dp, colors.primary))
            }
            // Always on screen while Claude waits for an answer, whether or not the chat panel is open.
            pendingConfirm?.let { description ->
                ConfirmBar(description, onAnswer = chat::answerConfirm)
            }
            if (chatOpen) {
                ChatPanel(
                    model = chat,
                    expanded = chatExpanded,
                    onToggleExpanded = {
                        chatExpanded = !chatExpanded
                        if (!chatExpanded) chatFraction = PANEL_DEFAULT
                    },
                    onDrag = { dragPx ->
                        // Pulling down from full screen starts from just under the top.
                        val from = if (chatExpanded) 1f else chatFraction
                        chatExpanded = false
                        chatFraction = dragPanel(from, dragPx, areaPx.toFloat())
                    },
                    onDragEnd = {
                        when (val rest = restPanel(chatFraction)) {
                            PanelRest.Full -> chatExpanded = true
                            PanelRest.Closed -> {
                                chatOpen = false
                                chatFraction = PANEL_DEFAULT
                            }
                            is PanelRest.Partial -> chatFraction = rest.fraction
                        }
                    },
                    onClose = { chatOpen = false },
                    onOpenSettings = onSettings,
                    modifier = Modifier.weight(if (fullChat) 1f else chatFraction.coerceIn(0.05f, 0.95f)),
                )
            } else if (agentRunning) {
                RunningStrip(onOpen = { chatOpen = true }, onStop = chat::stop)
            }
        }
        if (!fullChat) {
            CommandBar(
                controller = controller,
                onStartPage = onStartPage,
                chatOpen = chatOpen,
                agentRunning = agentRunning,
                isBookmarked = isBookmarked,
                onClaude = { chatOpen = !chatOpen },
                onToggleBookmark = onToggleBookmark,
                onBookmarks = onBookmarks,
                onSettings = onSettings,
            )
        }
    }
}

/** The one floating bar at the bottom: back, the address, Claude, and a menu for the rest. */
@Composable
private fun CommandBar(
    controller: BrowserController,
    onStartPage: Boolean,
    chatOpen: Boolean,
    agentRunning: Boolean,
    isBookmarked: Boolean,
    onClaude: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit,
) {
    val url by controller.url.collectAsState()
    val progress by controller.progress.collectAsState()
    val canGoBack by controller.canGoBack.collectAsState()
    val canGoForward by controller.canGoForward.collectAsState()
    val loading = progress in 1..99 && !onStartPage
    // Editing swaps the read-only host for a fresh text field, so the field always starts from the full URL.
    var editing by remember { mutableStateOf<TextFieldValue?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme

    BackHandler(enabled = editing != null) { editing = null }

    Surface(
        color = colors.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).height(56.dp),
    ) {
        Box {
            val draft = editing
            if (draft != null) {
                val requester = remember { FocusRequester() }
                var hadFocus by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { requester.requestFocus() }
                Row(Modifier.fillMaxSize().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillField(
                        value = draft,
                        onValueChange = { editing = it },
                        placeholder = "검색어 또는 주소 입력",
                        minHeight = 44.dp,
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
            } else {
                Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = controller::back, enabled = canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(onClickLabel = "주소 편집") {
                                val text = if (onStartPage) "" else url
                                editing = TextFieldValue(text, TextRange(0, text.length))
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            when {
                                onStartPage -> Icons.Outlined.Search
                                url.startsWith("https://") -> Icons.Outlined.Lock
                                else -> Icons.Outlined.Public
                            },
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (onStartPage) "검색어 또는 주소 입력" else displayHost(url),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (onStartPage) FontWeight.Normal else FontWeight.Medium,
                            color = if (onStartPage) colors.onSurfaceVariant else colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    ClaudeOrb(active = chatOpen, running = agentRunning, onClick = onClaude)
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "메뉴")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            containerColor = colors.surfaceContainerHigh,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, colors.outlineVariant),
                        ) {
                            @Composable
                            fun item(label: String, icon: ImageVector, enabled: Boolean = true, action: () -> Unit) {
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = { Icon(icon, contentDescription = null) },
                                    enabled = enabled,
                                    onClick = {
                                        menuOpen = false
                                        action()
                                    },
                                )
                            }
                            item("앞으로", Icons.AutoMirrored.Filled.ArrowForward, enabled = canGoForward, action = controller::forward)
                            if (loading) item("로딩 중지", Icons.Filled.Close, action = controller::stop)
                            else item("새로고침", Icons.Filled.Refresh, enabled = !onStartPage, action = controller::reload)
                            if (isBookmarked) item("북마크에서 삭제", Icons.Filled.Star, action = onToggleBookmark)
                            else item("북마크에 추가", Icons.Outlined.StarBorder, enabled = url.startsWith("http"), action = onToggleBookmark)
                            item("북마크", Icons.Outlined.Bookmarks, action = onBookmarks)
                            item("홈", Icons.Outlined.Home, enabled = !onStartPage, action = controller::home)
                            item("설정", Icons.Outlined.Settings, action = onSettings)
                        }
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

/** The app's mark as a button: filled when the panel is open, circled by a spinner while Claude works. */
@Composable
private fun ClaudeOrb(active: Boolean, running: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (active) colors.primary else colors.primaryContainer)
                .clickable(onClickLabel = "Claude 패널", role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_mark),
                contentDescription = "Claude",
                tint = if (active) colors.onPrimary else colors.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        if (running) CircularProgressIndicator(Modifier.size(48.dp), strokeWidth = 2.dp, color = colors.primary)
    }
}

@Composable
private fun RunningStrip(onOpen: () -> Unit, onStop: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.primaryContainer,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 6.dp).height(44.dp),
    ) {
        Row(
            Modifier.fillMaxSize().clickable(onClickLabel = "대화 열기", onClick = onOpen).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
