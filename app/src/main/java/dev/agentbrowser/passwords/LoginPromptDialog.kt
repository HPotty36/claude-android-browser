package dev.agentbrowser.passwords

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LoginPromptDialog(prompt: LoginPrompt) {
    when (prompt) {
        is LoginPrompt.Save -> AlertDialog(
            onDismissRequest = { prompt.respond(false) },
            title = { Text("비밀번호를 저장할까요?") },
            text = { Text("${prompt.origin}\n${prompt.username.ifEmpty { "(아이디 없음)" }}") },
            confirmButton = { TextButton(onClick = { prompt.respond(true) }) { Text("저장") } },
            dismissButton = { TextButton(onClick = { prompt.respond(false) }) { Text("저장 안 함") } },
        )
        is LoginPrompt.Select -> AlertDialog(
            onDismissRequest = { prompt.respond(null) },
            title = { Text("로그인할 계정 선택") },
            text = {
                Column {
                    prompt.usernames.forEachIndexed { index, username ->
                        Text(
                            username.ifEmpty { "(아이디 없음)" },
                            modifier = Modifier.fillMaxWidth().clickable { prompt.respond(index) }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { prompt.respond(null) }) { Text("취소") } },
        )
    }
}
