package dev.agentbrowser.passwords

import dev.agentbrowser.ui.LocalStrings
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
    val s = LocalStrings.current
    when (prompt) {
        is LoginPrompt.Save -> AlertDialog(
            onDismissRequest = { prompt.respond(false) },
            title = { Text(s.savePasswordTitle) },
            text = { Text("${prompt.origin}\n${prompt.username.ifEmpty { s.noUsername }}") },
            confirmButton = { TextButton(onClick = { prompt.respond(true) }) { Text(s.save) } },
            dismissButton = { TextButton(onClick = { prompt.respond(false) }) { Text(s.notNow) } },
        )
        is LoginPrompt.Select -> AlertDialog(
            onDismissRequest = { prompt.respond(null) },
            title = { Text(s.chooseAccount) },
            text = {
                Column {
                    prompt.usernames.forEachIndexed { index, username ->
                        Text(
                            username.ifEmpty { s.noUsername },
                            modifier = Modifier.fillMaxWidth().clickable { prompt.respond(index) }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { prompt.respond(null) }) { Text(s.cancel) } },
        )
    }
}
