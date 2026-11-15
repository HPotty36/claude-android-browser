package dev.agentbrowser.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraSections: @Composable () -> Unit = {},
) {
    val hint by settings.apiKeyHint.collectAsState()
    val model by settings.model.collectAsState()
    var keyDraft by remember { mutableStateOf("") }
    var customModel by remember { mutableStateOf(if (model in KNOWN_MODELS) "" else model) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 뒤로") }
            Text("설정", style = MaterialTheme.typography.titleLarge)
        }

        Text("Anthropic API 키", style = MaterialTheme.typography.titleMedium)
        Text(
            if (hint != null) "저장됨 · 끝 4자리 $hint" else "저장된 키가 없어요.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = keyDraft,
            onValueChange = { keyDraft = it },
            singleLine = true,
            placeholder = { Text("sk-ant-...") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    settings.setApiKey(keyDraft)
                    keyDraft = ""
                },
                enabled = keyDraft.isNotBlank(),
            ) { Text("저장") }
            OutlinedButton(onClick = { settings.setApiKey(null) }, enabled = hint != null) { Text("삭제") }
        }
        Text(
            "키는 이 기기에만 암호화되어 저장돼요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()
        Text("모델", style = MaterialTheme.typography.titleMedium)
        KNOWN_MODELS.forEach { name ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = model == name, onClick = { settings.setModel(name) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = model == name, onClick = { settings.setModel(name) })
                Text(name)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = customModel,
                onValueChange = { customModel = it },
                singleLine = true,
                placeholder = { Text("다른 모델 이름 직접 입력") },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { settings.setModel(customModel) }, enabled = customModel.isNotBlank()) {
                Text("적용")
            }
        }
        if (model !in KNOWN_MODELS) Text("현재 모델: $model", color = MaterialTheme.colorScheme.onSurfaceVariant)

        extraSections()
    }
}
