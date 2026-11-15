package dev.agentbrowser.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.agentbrowser.ui.ScreenScaffold
import dev.agentbrowser.ui.SectionLabel

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    extraSections: @Composable (snackbar: SnackbarHostState) -> Unit = {},
) {
    ScreenScaffold(title = "설정", onBack = onBack) { snackbar ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionLabel("Anthropic API 키")
            ApiKeyField(settings)
            SectionLabel("모델")
            ModelPicker(settings)
            extraSections(snackbar)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ApiKeyField(settings: AppSettings) {
    val hint by settings.apiKeyHint.collectAsState()
    var draft by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            label = { Text("API 키") },
            placeholder = { Text("sk-ant-…") },
            supportingText = {
                Text(if (hint != null) "저장됨 · ····$hint" else "이 기기에만 암호화해 저장해요.")
            },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (visible) "키 숨기기" else "키 보기",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (hint != null) {
                TextButton(
                    onClick = { settings.setApiKey(null) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("삭제") }
            }
            Button(
                onClick = {
                    settings.setApiKey(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank(),
            ) { Text("저장") }
        }
    }
}

@Composable
private fun ModelPicker(settings: AppSettings) {
    val model by settings.model.collectAsState()
    val isCustom = KNOWN_MODELS.none { it.first == model }
    var customSelected by remember { mutableStateOf(isCustom) }
    var customDraft by remember { mutableStateOf(if (isCustom) model else "") }
    val focus = LocalFocusManager.current
    val rowColors = ListItemDefaults.colors(containerColor = Color.Transparent)

    KNOWN_MODELS.forEach { (id, label) ->
        val selected = !customSelected && model == id
        ListItem(
            headlineContent = { Text(label) },
            supportingContent = { Text(id, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) },
            leadingContent = { RadioButton(selected = selected, onClick = null) },
            colors = rowColors,
            modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) {
                customSelected = false
                settings.setModel(id)
            },
        )
    }
    ListItem(
        headlineContent = { Text("직접 입력") },
        leadingContent = { RadioButton(selected = customSelected, onClick = null) },
        colors = rowColors,
        modifier = Modifier.selectable(selected = customSelected, role = Role.RadioButton) { customSelected = true },
    )
    if (customSelected) {
        OutlinedTextField(
            value = customDraft,
            onValueChange = { customDraft = it },
            singleLine = true,
            placeholder = { Text("모델 ID") },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (customDraft.isNotBlank()) settings.setModel(customDraft)
                focus.clearFocus()
            }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
}
