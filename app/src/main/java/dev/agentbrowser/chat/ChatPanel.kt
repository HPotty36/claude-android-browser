package dev.agentbrowser.chat

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import android.view.KeyCharacterMap
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.channels.ReceiveChannel
import dev.agentbrowser.ui.LocalStrings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agentbrowser.ui.PillField

/** The lower half of the browser screen: the conversation with Claude and what it is doing to the page. */
@Composable
fun ChatPanel(
    model: ChatViewModel,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    suggestions: List<String>,
    onDrag: (dragPx: Float) -> Unit,
    onDragEnd: () -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    beside: Boolean = false,
    focusRequests: ReceiveChannel<Unit>? = null,
    bottomInset: Dp = 0.dp,
) {
    val items by model.items.collectAsState()
    val running by model.running.collectAsState()
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    val colors = MaterialTheme.colorScheme
    val s = LocalStrings.current
    // The header's gesture outlives a move between the bottom and the side, so it must call what is current.
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val focusManager = LocalFocusManager.current
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(focusRequests) {
        if (focusRequests != null) for (request in focusRequests) runCatching { inputFocus.requestFocus() }
    }
    // A keyboard plugged in (USB, Bluetooth, a dock): Enter sends, Shift+Enter breaks the line.
    val configuration = LocalConfiguration.current
    val hardwareKeyboard = configuration.keyboard == Configuration.KEYBOARD_QWERTY
    var shiftHeld by remember { mutableStateOf(false) }
    val submit = {
        // While Claude works the model takes nothing, so the draft stays for later.
        if (!running && draft.text.isNotBlank()) {
            model.send(draft.text)
            draft = TextFieldValue("")
        }
    }

    LaunchedEffect(items.size, running) {
        val last = items.size + (if (running) 1 else 0) - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Surface(
        color = colors.surfaceContainerLow,
        shape = if (expanded || beside) RectangleShape else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = if (expanded || beside) null else BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            // The whole header is the grip: drag it to resize the panel.
            Column(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { dragEnd() },
                        onDragCancel = { dragEnd() },
                    ) { change, dragAmount ->
                        change.consume()
                        drag(dragAmount)
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Beside the page there is nothing to drag, so no grip is drawn.
                if (!beside) Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(colors.outline, RoundedCornerShape(2.dp))
                        .semantics { contentDescription = s.resizePanel },
                )
            Row(
                Modifier.fillMaxWidth().height(44.dp).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Claude", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { model.newChat() }) {
                    Icon(Icons.Outlined.AddComment, contentDescription = s.newChat, tint = colors.onSurfaceVariant)
                }
                IconButton(onClick = onToggleExpanded) {
                    if (expanded) {
                        Icon(Icons.Filled.CloseFullscreen, contentDescription = s.shrinkPanel, tint = colors.onSurfaceVariant)
                    } else {
                        Icon(Icons.Filled.OpenInFull, contentDescription = s.expandPanel, tint = colors.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = s.closePanel, tint = colors.onSurfaceVariant)
                }
            }
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (items.isEmpty()) {
                    item {
                        Column {
                            suggestions.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = { model.send(suggestion) },
                                    label = { Text(suggestion) },
                                    border = BorderStroke(1.dp, colors.outlineVariant),
                                )
                            }
                        }
                    }
                }
                itemsIndexed(items) { index, item ->
                    // Consecutive actions sit close together; a new turn gets more air.
                    val previous = items.getOrNull(index - 1)
                    val gap = when {
                        previous == null -> 0.dp
                        item is ChatItem.Action && previous is ChatItem.Action -> 4.dp
                        else -> 12.dp
                    }
                    Spacer(Modifier.height(gap))
                    ChatRow(item, onOpenSettings)
                }
                if (running) {
                    item {
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(s.working, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(
                // A bar floating over the panel's foot must not cover the message box.
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).padding(bottom = bottomInset),
                verticalAlignment = Alignment.Bottom,
            ) {
                PillField(
                    value = draft,
                    onValueChange = { value ->
                        // With a keyboard plugged in, Enter sends even when the keyboard app turned it into a line break.
                        val typed = if (hardwareKeyboard && !shiftHeld) {
                            typedLineBreak(draft.text, value.text, value.selection.start).takeIf { value.selection.collapsed }
                        } else {
                            null
                        }
                        if (typed != null) {
                            draft = TextFieldValue(typed, TextRange(value.selection.start - 1))
                            submit()
                        } else {
                            draft = value
                        }
                    },
                    placeholder = s.taskHint,
                    minHeight = 44.dp,
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.weight(1f).focusRequester(inputFocus).onPreviewKeyEvent { event ->
                        val key = event.nativeKeyEvent
                        // Shift+Enter reaches the keyboard app, not here; its Shift does, and says a line break is wanted.
                        shiftHeld = key.isShiftPressed
                        // An on-screen keyboard's Enter stays a line break: there the button sends.
                        val hardware = key.deviceId != KeyCharacterMap.VIRTUAL_KEYBOARD
                        val down = key.action == AndroidKeyEvent.ACTION_DOWN
                        val enter = key.keyCode == AndroidKeyEvent.KEYCODE_ENTER || key.keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                        when {
                            !hardware -> false
                            sendsMessage(key.keyCode, key.isShiftPressed, key.isCtrlPressed, key.isAltPressed) -> {
                                if (down && key.repeatCount == 0) submit()
                                true
                            }
                            enter -> {
                                if (down) {
                                    val (text, cursor) = withNewLine(draft.text, draft.selection.start, draft.selection.end)
                                    draft = TextFieldValue(text, TextRange(cursor))
                                }
                                true
                            }
                            key.keyCode == AndroidKeyEvent.KEYCODE_TAB && !key.isCtrlPressed && !key.isAltPressed -> {
                                if (down) focusManager.moveFocus(if (key.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
                                true
                            }
                            else -> false
                        }
                    },
                    trailing = { Spacer(Modifier.width(12.dp)) },
                )
                Spacer(Modifier.width(8.dp))
                if (running) {
                    FilledIconButton(
                        onClick = { model.stop() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.error,
                            contentColor = colors.onError,
                        ),
                        modifier = Modifier.size(44.dp),
                    ) { Icon(Icons.Filled.Stop, contentDescription = s.stop) }
                } else {
                    FilledIconButton(
                        onClick = submit,
                        enabled = draft.text.isNotBlank(),
                        modifier = Modifier.size(44.dp),
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = s.send) }
                }
            }
        }
    }
}

/** Asks before a risky action. Shown by the browser screen so it stays visible with the panel closed. */
@Composable
fun ConfirmBar(description: String, onAnswer: (approved: Boolean) -> Unit, bottomInset: Dp = 0.dp) {
    val colors = MaterialTheme.colorScheme
    val s = LocalStrings.current
    Surface(color = colors.surfaceContainerHighest) {
        Column {
            HorizontalDivider(color = colors.outlineVariant)
            Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = bottomInset)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = colors.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(s.confirmTitle, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onAnswer(false) }) { Text(s.cancel) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onAnswer(true) }) { Text(s.proceed) }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(item: ChatItem, onOpenSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val s = LocalStrings.current
    when (item) {
        is ChatItem.User -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                item.text,
                color = colors.onPrimaryContainer,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(colors.primaryContainer, RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        is ChatItem.Assistant -> Text(
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            text = buildAnnotatedString {
                boldSegments(item.text).forEach { segment ->
                    if (segment.bold) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(segment.text) }
                    else append(segment.text)
                }
            },
        )
        is ChatItem.Action -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(item.text, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        is ChatItem.Notice -> Column {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = colors.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(item.text, color = colors.error, modifier = Modifier.weight(1f))
            }
            if (item.openSettings) TextButton(onClick = onOpenSettings) { Text(s.openSettings) }
        }
    }
}
