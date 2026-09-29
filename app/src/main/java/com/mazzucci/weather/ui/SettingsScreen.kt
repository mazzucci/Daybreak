@file:OptIn(ExperimentalMaterial3Api::class)

package com.mazzucci.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mazzucci.weather.domain.AppSettings
import com.mazzucci.weather.domain.TempUnit
import com.mazzucci.weather.narration.GemmaModelSource
import com.mazzucci.weather.narration.ModelStatus
import java.util.Locale

private val ScreenMargin = 16.dp
private const val MODEL_SIZE_HINT = "about 550 MB"

@Composable
fun SettingsScreen(
    settings: AppSettings,
    modelStatus: ModelStatus,
    onUnitChange: (TempUnit) -> Unit,
    onGemmaEnabledChange: (Boolean) -> Unit,
    onMemesEnabledChange: (Boolean) -> Unit,
    onDownloadModel: (hfToken: String) -> Unit,
    onCancelDownload: () -> Unit,
    onImportModel: () -> Unit,
    onRemoveModel: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenMargin),
        ) {
            Spacer(Modifier.height(4.dp))
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Temperature", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    // The chosen unit is the big number; the other one is shown small next to it.
                    SingleChoiceSegmentedButtonRow(Modifier.width(200.dp)) {
                        TempUnit.entries.forEachIndexed { i, unit ->
                            SegmentedButton(
                                selected = settings.primaryUnit == unit,
                                onClick = { onUnitChange(unit) },
                                shape = SegmentedButtonDefaults.itemShape(i, TempUnit.entries.size),
                            ) { Text(if (unit == TempUnit.F) "°F first" else "°C first") }
                        }
                    }
                }
            }

            SectionTitle("Fun")
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily weather meme", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "A silly two-line caption under each forecast, made on this phone. " +
                                "Switch Gemma on below for a fresh one every day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = settings.memesEnabled,
                        onCheckedChange = onMemesEnabledChange,
                        modifier = Modifier.semantics { contentDescription = "Daily weather meme" },
                    )
                }
            }

            SectionTitle("AI summary")
            SettingsCard {
                val modelInstalled = modelStatus is ModelStatus.Installed
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Describe the weather with Gemma", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (modelInstalled) {
                                "Runs entirely on this phone; the standard summary stays if Gemma's text doesn't match the forecast."
                            } else {
                                "Download or import the model below to turn this on."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    // Without a model the switch would promise something that can't happen, so show it off and disabled.
                    Switch(
                        checked = settings.gemmaEnabled && modelInstalled,
                        onCheckedChange = onGemmaEnabledChange,
                        enabled = modelInstalled,
                        modifier = Modifier.semantics { contentDescription = "Describe the weather with Gemma" },
                    )
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
                ModelSection(
                    status = modelStatus,
                    onDownloadModel = onDownloadModel,
                    onCancelDownload = onCancelDownload,
                    onImportModel = onImportModel,
                    onRemoveModel = onRemoveModel,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/** The whole Gemma model story: what's installed, and a numbered walk-through when nothing is. */
@Composable
private fun ModelSection(
    status: ModelStatus,
    onDownloadModel: (String) -> Unit,
    onCancelDownload: () -> Unit,
    onImportModel: () -> Unit,
    onRemoveModel: () -> Unit,
) {
    Text("Gemma model", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(12.dp))
    when (status) {
        is ModelStatus.Installed -> InstalledState(status, onRemoveModel)
        is ModelStatus.Downloading -> DownloadingState(status, onCancelDownload)
        ModelStatus.Verifying -> BusyState("Checking the download…", "Making sure the file arrived intact.")
        ModelStatus.Importing -> BusyState("Copying the model…", "This can take a minute for a large file.")
        ModelStatus.NotInstalled -> SetupSteps(onDownloadModel, onImportModel)
        is ModelStatus.Failed -> {
            FailedBanner(status.message)
            Spacer(Modifier.height(16.dp))
            SetupSteps(onDownloadModel, onImportModel)
        }
    }
}

@Composable
private fun InstalledState(status: ModelStatus.Installed, onRemoveModel: () -> Unit) {
    val success = MaterialTheme.weatherColors.success
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(success.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(20.dp), tint = success) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Installed", style = MaterialTheme.typography.titleMedium)
            Text(
                "${formatSize(status.sizeBytes)} in app storage; summaries never leave the phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    OutlinedButton(onRemoveModel, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
        Text("Remove model")
    }
}

@Composable
private fun DownloadingState(status: ModelStatus.Downloading, onCancelDownload: () -> Unit) {
    val total = status.totalBytes
    val paused = status.pausedReason
    val fraction = total?.let { (status.downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
    val title = when {
        paused != null -> "Download paused"
        total == null -> "Starting download…"
        else -> "Downloading"
    }
    val progress = when {
        total != null -> "${formatSize(status.downloadedBytes)} of ${formatSize(total)}" +
            (fraction?.let { " · ${(it * 100).toInt()}%" } ?: "")
        else -> "Asking Hugging Face for the file"
    }
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    if (paused != null) {
        Text(paused, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(2.dp))
    }
    Text(progress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    val bar = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
    when {
        // A paused download keeps its (static) progress on screen; the paused text above says why it isn't moving.
        fraction != null -> LinearProgressIndicator({ fraction }, bar)
        else -> LinearProgressIndicator(bar)
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "You can leave the app; the download continues in the background and shows in your notifications.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onCancelDownload) { Text("Cancel download") }
}

@Composable
private fun BusyState(title: String, detail: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
}

@Composable
private fun FailedBanner(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "Setup didn't finish",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                "Check the steps below and try again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/**
 * The guided download. The token lives only in composition memory while the screen is open (deliberately not
 * rememberSaveable, so it never enters saved instance state); it's passed to [onDownloadModel] once and never
 * written anywhere.
 */
@Composable
private fun SetupSteps(onDownloadModel: (String) -> Unit, onImportModel: () -> Unit) {
    var token by remember { mutableStateOf("") }

    Step(1, "Accept the license", "Sign in to Hugging Face (free) and accept Google's terms on the model page.") {
        LinkButton("Open model page", GemmaModelSource.MODEL_PAGE)
    }
    Step(2, "Create a read token", "In your account settings, make an access token with read permission and copy it.") {
        LinkButton("Get a token", GemmaModelSource.TOKENS_PAGE)
    }
    Step(3, "Paste the token and download", "One-time download, $MODEL_SIZE_HINT, Wi-Fi recommended. The token is used once to start it and isn't stored.", last = true) {
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Hugging Face token") },
            placeholder = { Text("hf_…") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            { onDownloadModel(token); token = "" },
            enabled = token.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Download Gemma") }
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Already have ${GemmaModelSource.FILE_NAME}?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onImportModel) { Text("Import file…") }
    }
}

/**
 * Opens [url] in the browser; devices without one (or with it disabled) get the address inline instead of a
 * crash, since [androidx.compose.ui.platform.UriHandler.openUri] throws when no activity can handle it.
 */
@Composable
private fun LinkButton(label: String, url: String) {
    val uriHandler = LocalUriHandler.current
    var failed by remember { mutableStateOf(false) }
    OutlinedButton({ failed = runCatching { uriHandler.openUri(url) }.isFailure }) { Text(label) }
    if (failed) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Couldn't open a browser. On another device, visit ${url.removePrefix("https://")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * One numbered step: a badge and a connector line on the left, title/text/action on the right. The row is
 * sized to its content's intrinsic height so the connector can fill it (a weight inside the scrolling column
 * would get no height at all).
 */
@Composable
private fun Step(number: Int, title: String, text: String, last: Boolean = false, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            if (!last) {
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            action()
        }
    }
}

private fun formatSize(bytes: Long): String =
    String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
