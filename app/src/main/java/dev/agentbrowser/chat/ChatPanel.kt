package dev.agentbrowser.chat

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

private val SUGGESTIONS = listOf("이 페이지 요약해 줘", "핵심만 세 줄로 정리해 줘", "이 페이지에서 가격 찾아 줘")

/** The lower half of the browser screen: the conversation with Claude and what it is doing to the page. */
@Composable
fun ChatPanel(
    model: ChatViewModel,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDrag: (dragPx: Float) -> Unit,
    onDragEnd: () -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by model.items.collectAsState()
    val running by model.running.collectAsState()
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(items.size, running) {
        val last = items.size + (if (running) 1 else 0) - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Surface(
        color = colors.surfaceContainerLow,
        shape = if (expanded) RectangleShape else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = if (expanded) null else BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            // The whole header is the grip: drag it to resize the panel.
            Column(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                    ) { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(colors.outline, RoundedCornerShape(2.dp))
                        .semantics { contentDescription = "끌어서 패널 크기 조절" },
                )
            Row(
                Modifier.fillMaxWidth().height(44.dp).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Claude", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { model.newChat() }) {
                    Icon(Icons.Outlined.AddComment, contentDescription = "새 대화", tint = colors.onSurfaceVariant)
                }
                IconButton(onClick = onToggleExpanded) {
                    if (expanded) {
                        Icon(Icons.Filled.CloseFullscreen, contentDescription = "패널 줄이기", tint = colors.onSurfaceVariant)
                    } else {
                        Icon(Icons.Filled.OpenInFull, contentDescription = "전체 화면으로 키우기", tint = colors.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "패널 접기", tint = colors.onSurfaceVariant)
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
                            Text(
                                "이 페이지에서 할 일을 적어 주세요.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            SUGGESTIONS.forEach { suggestion ->
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
                            Text("작업 중", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                PillField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = "이 페이지에서 할 일",
                    minHeight = 44.dp,
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
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
                    ) { Icon(Icons.Filled.Stop, contentDescription = "중지") }
                } else {
                    FilledIconButton(
                        onClick = {
                            model.send(draft.text)
                            draft = TextFieldValue("")
                        },
                        enabled = draft.text.isNotBlank(),
                        modifier = Modifier.size(44.dp),
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "보내기") }
                }
            }
        }
    }
}

/** Asks before a risky action. Shown by the browser screen so it stays visible with the panel closed. */
@Composable
fun ConfirmBar(description: String, onAnswer: (approved: Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainerHighest) {
        Column {
            HorizontalDivider(color = colors.outlineVariant)
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = colors.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("이 작업을 진행할까요?", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onAnswer(false) }) { Text("취소") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onAnswer(true) }) { Text("진행") }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(item: ChatItem, onOpenSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
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
            if (item.openSettings) TextButton(onClick = onOpenSettings) { Text("설정 열기") }
        }
    }
}
