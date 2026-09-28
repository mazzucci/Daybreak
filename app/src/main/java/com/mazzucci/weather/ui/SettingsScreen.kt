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
import androidx.compose.material3.TopAppBar
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
    onImportModel: () -> Unit,
    onRemoveModel: () -> Unit,
    onBack: () -> Unit,
) {
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
                if (modelStatus is ModelStatus.Importing) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onImportModel, enabled = modelStatus !is ModelStatus.Importing) {
                        Text(if (modelStatus is ModelStatus.Installed) "Replace model" else "Import model")
                    }
                    if (modelStatus is ModelStatus.Installed) {
                        OutlinedButton(onRemoveModel) { Text("Remove") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Download gemma3-1b-it-int4.task (about 550 MB) from huggingface.co/litert-community/Gemma3-1B-IT " +
                        "after accepting the Gemma license, then import it here. The file is copied into the app; " +
                        "you can delete the download afterwards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    is ModelStatus.Installed -> "Gemma installed (${formatSize(status.sizeBytes)})"
    is ModelStatus.Failed -> "Import failed: ${status.message}"
}

private fun formatSize(bytes: Long): String =
    String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
