@file:OptIn(ExperimentalMaterial3Api::class)

package com.mazzucci.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.mazzucci.weather.narration.GemmaModelSource
import com.mazzucci.weather.narration.isBusy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mazzucci.weather.domain.AppSettings
import com.mazzucci.weather.domain.TempUnit
import com.mazzucci.weather.narration.ModelStatus
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: AppSettings,
    modelStatus: ModelStatus,
    onUnitChange: (TempUnit) -> Unit,
    onGemmaEnabledChange: (Boolean) -> Unit,
    onDownloadModel: (hfToken: String) -> Unit,
    onCancelDownload: () -> Unit,
    onImportModel: () -> Unit,
    onRemoveModel: () -> Unit,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var token by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionTitle("Temperature")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                TempUnit.entries.forEachIndexed { i, unit ->
                    SegmentedButton(
                        selected = settings.primaryUnit == unit,
                        onClick = { onUnitChange(unit) },
                        shape = SegmentedButtonDefaults.itemShape(i, TempUnit.entries.size),
                    ) { Text(if (unit == TempUnit.F) "°F first" else "°C first") }
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()

            SectionTitle("AI summary")
            ListItem(
                headlineContent = { Text("Describe the weather with Gemma") },
                supportingContent = {
                    Text("Runs entirely on this phone. If Gemma's text doesn't match the forecast, the standard summary is shown instead.")
                },
                trailingContent = { Switch(settings.gemmaEnabled, onGemmaEnabledChange) },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Model", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(modelStatusText(modelStatus), style = MaterialTheme.typography.bodyMedium)
                if (modelStatus.isBusy) {
                    Spacer(Modifier.height(8.dp))
                    val progress = (modelStatus as? ModelStatus.Downloading)?.let { d ->
                        d.totalBytes?.let { d.downloadedBytes.toFloat() / it }
                    }
                    if (progress != null) LinearProgressIndicator({ progress }, Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(12.dp))
                when {
                    modelStatus is ModelStatus.Downloading -> {
                        OutlinedButton(onCancelDownload) { Text("Cancel download") }
                    }
                    modelStatus is ModelStatus.Installed -> OutlinedButton(onRemoveModel) { Text("Remove model") }
                    !modelStatus.isBusy -> {
                        Text(
                            "1. Open the model page and accept the Gemma license (free Hugging Face account).\n" +
                                "2. Create a read access token and paste it below.\n" +
                                "3. Download (about 550 MB; Wi-Fi recommended). The token is only used to start the download.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ uriHandler.openUri(GemmaModelSource.MODEL_PAGE) }) { Text("Model page") }
                            OutlinedButton({ uriHandler.openUri(GemmaModelSource.TOKENS_PAGE) }) { Text("Get token") }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("Hugging Face token") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ onDownloadModel(token) }, enabled = token.isNotBlank()) { Text("Download Gemma") }
                            TextButton(onImportModel) { Text("Import file…") }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

private fun modelStatusText(status: ModelStatus): String = when (status) {
    ModelStatus.NotInstalled -> "No model imported"
    ModelStatus.Importing -> "Importing…"
    is ModelStatus.Downloading -> when {
        status.waitingForNetwork -> "Download paused, waiting for a network connection…"
        status.totalBytes != null ->
            "Downloading… ${formatSize(status.downloadedBytes)} of ${formatSize(status.totalBytes)}"
        else -> "Starting download…"
    }
    ModelStatus.Verifying -> "Checking the download…"
    is ModelStatus.Installed -> "Gemma installed (${formatSize(status.sizeBytes)})"
    is ModelStatus.Failed -> status.message
}

private fun formatSize(bytes: Long): String =
    String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
