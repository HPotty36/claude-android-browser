package dev.agentbrowser.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp

/** The lower half of the browser screen: the conversation with Claude and what it is doing to the page. */
@Composable
fun ChatPanel(
    model: ChatViewModel,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by model.items.collectAsState()
    val running by model.running.collectAsState()
    val pendingConfirm by model.pendingConfirm.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(items.size, pendingConfirm) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }

    Column(modifier.fillMaxWidth()) {
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Claude", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { model.newChat() }) { Text("새 대화") }
            TextButton(onClick = onClose) { Text("닫기") }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    Text(
                        "이 페이지에서 할 일을 말해 보세요. 예: \"이 페이지 요약해줘\"",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items) { item -> ChatRow(item, onOpenSettings) }
        }
        pendingConfirm?.let { description ->
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
            ) {
                Text("진행할까요? $description", color = MaterialTheme.colorScheme.onErrorContainer)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { model.answerConfirm(true) }) { Text("진행") }
                    OutlinedButton(onClick = { model.answerConfirm(false) }) { Text("취소") }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Claude에게 시키기") },
                maxLines = 3,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.padding(4.dp))
            if (running) {
                Button(onClick = { model.stop() }) { Text("정지") }
            } else {
                Button(
                    onClick = {
                        model.send(draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank(),
                ) { Text("보내기") }
            }
        }
    }
}

@Composable
private fun ChatRow(item: ChatItem, onOpenSettings: () -> Unit) {
    when (item) {
        is ChatItem.User -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                item.text,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        is ChatItem.Assistant -> Text(item.text)
        is ChatItem.Action -> Text(
            "· ${item.text}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is ChatItem.Notice -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.text, color = MaterialTheme.colorScheme.error)
            if (item.openSettings) TextButton(onClick = onOpenSettings) { Text("설정 열기") }
        }
    }
}
