package com.mazzucci.weather.narration

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ModelStatus {
    data object NotInstalled : ModelStatus
    data object Importing : ModelStatus
    data class Installed(val sizeBytes: Long) : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

/** Manages the on-device model file that the user imports (it's too big, and license-gated, to ship in the APK). */
interface LocalModelManager {
    val status: StateFlow<ModelStatus>

    /** Copies the model from a content:// [uri] chosen in the system file picker. */
    suspend fun import(uri: String)
    fun remove()
}

/** Keeps a single imported Gemma `.task` file in app-private storage. */
class GemmaModelStore(private val context: Context) : LocalModelManager {
    private val dir = File(context.filesDir, "models")
    val modelFile = File(dir, "gemma.task")

    private val _status = MutableStateFlow(currentStatus())
    override val status: StateFlow<ModelStatus> = _status.asStateFlow()

    /** The model file if one is installed, else null. */
    fun installedFile(): File? = modelFile.takeIf { it.isFile && it.length() > 0 }

    override suspend fun import(uri: String) {
        _status.value = ModelStatus.Importing
        _status.value = try {
            withContext(Dispatchers.IO) { copyIn(Uri.parse(uri)) }
            currentStatus()
        } catch (e: Exception) {
            ModelStatus.Failed(e.message ?: "Couldn't import the model")
        }
    }

    override fun remove() {
        modelFile.delete()
        _status.value = currentStatus()
    }

    private fun copyIn(uri: Uri) {
        val name = displayName(uri)
        require(name == null || name.endsWith(".task", ignoreCase = true)) {
            "Pick a MediaPipe .task model file (e.g. gemma3-1b-it-int4.task)"
        }
        dir.mkdirs()
        // Copy to a temp file first so a failed or partial import never replaces a working model.
        val tmp = File(dir, "gemma.task.part")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Couldn't open the selected file" }
            tmp.outputStream().use { input.copyTo(it, bufferSize = 1 shl 20) }
        }
        check(tmp.length() > MIN_MODEL_BYTES) { "That file is too small to be a Gemma model" }
        modelFile.delete()
        check(tmp.renameTo(modelFile)) { "Couldn't save the model" }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun currentStatus(): ModelStatus =
        installedFile()?.let { ModelStatus.Installed(it.length()) } ?: ModelStatus.NotInstalled

    private companion object {
        const val MIN_MODEL_BYTES = 10L * 1024 * 1024
    }
}
