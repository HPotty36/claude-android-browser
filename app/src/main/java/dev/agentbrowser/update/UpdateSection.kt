package dev.agentbrowser.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.agentbrowser.ui.LocalStrings
import dev.agentbrowser.ui.SectionLabel
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The "Update" part of the settings screen. Work is launched on [scope], which outlives the screen,
 * so a download carries on when settings is closed.
 */
@Composable
fun UpdateSection(updater: Updater, installedVersion: String, scope: CoroutineScope) {
    val s = LocalStrings.current
    val state by updater.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val note = MaterialTheme.typography.bodyMedium

    SectionLabel(s.update)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(s.currentVersion(installedVersion), style = MaterialTheme.typography.bodyLarge)
        if (!updater.enabled) {
            Text(s.updateDebugNote, style = note, color = colors.onSurfaceVariant)
            return@Column
        }

        @Composable
        fun checkButton(enabled: Boolean = true, label: String = s.checkForUpdates) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { scope.launch { updater.check() } }, enabled = enabled) { Text(label) }
        }

        @Composable
        fun found(release: Release) {
            Text(s.updateAvailable(release.version), style = note, color = colors.primary)
        }

        when (val st = state) {
            UpdateState.Idle -> checkButton()
            UpdateState.Checking -> checkButton(enabled = false, label = s.checkingForUpdates)
            UpdateState.UpToDate -> {
                Text(s.upToDate, style = note, color = colors.onSurfaceVariant)
                checkButton()
            }
            is UpdateState.Available -> {
                found(st.release)
                if (st.release.notes.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        st.release.notes,
                        style = note,
                        color = colors.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                val megabytes = (st.release.apkSize / 1_048_576.0).roundToInt().coerceAtLeast(1)
                Button(onClick = { scope.launch { updater.downloadAndInstall() } }) { Text(s.downloadUpdate(megabytes)) }
            }
            is UpdateState.Downloading -> {
                found(st.release)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { st.fraction }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(s.downloadingUpdate((st.fraction * 100).toInt()), style = note, color = colors.onSurfaceVariant)
            }
            is UpdateState.Installing -> {
                found(st.release)
                Text(s.installingUpdate, style = note, color = colors.onSurfaceVariant)
            }
            is UpdateState.Failed -> {
                Text(s.updateFailed, style = note, color = colors.error)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    scope.launch { if (st.release != null) updater.downloadAndInstall() else updater.check() }
                }) { Text(s.retry) }
            }
        }
    }
}
