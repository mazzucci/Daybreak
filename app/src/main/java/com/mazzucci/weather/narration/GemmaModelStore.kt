package com.mazzucci.weather.narration

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

sealed interface ModelStatus {
    data object NotInstalled : ModelStatus
    data object Importing : ModelStatus
    /** [totalBytes] is null until the server reports a size. */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?, val waitingForNetwork: Boolean = false) :
        ModelStatus
    data object Verifying : ModelStatus
    data class Installed(val sizeBytes: Long) : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

val ModelStatus.isBusy: Boolean
    get() = this is ModelStatus.Importing || this is ModelStatus.Downloading || this is ModelStatus.Verifying

/** Manages the on-device model file (it's too big, and license-gated, to ship in the APK). */
interface LocalModelManager {
    val status: StateFlow<ModelStatus>

    /** Downloads the model from Hugging Face using the user's access token. Returns once the download is queued. */
    suspend fun download(hfToken: String)
    fun cancelDownload()

    /** Copies the model from a content:// [uri] chosen in the system file picker. */
    suspend fun import(uri: String)
    fun remove()
}

/**
 * Keeps a single Gemma `.task` file in app-specific storage (no storage permission needed; removed on uninstall).
 *
 * Downloads go through the system DownloadManager, so they continue in the background with a notification and
 * survive the app being closed; an in-progress download is picked up again on the next launch.
 */
class GemmaModelStore(private val context: Context) : LocalModelManager {
    // External app-specific storage keeps a 550 MB file off the (often smaller) internal data partition,
    // and it's where DownloadManager can write, so a finished download is a cheap rename.
    private val dir = context.getExternalFilesDir(MODELS_DIR) ?: File(context.filesDir, MODELS_DIR)
    private val modelFile = File(dir, "gemma.task")
    private val partialFile = File(dir, PARTIAL_NAME)
    private val legacyFile = File(context.filesDir, "models/gemma.task") // location used by 1.0 imports

    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences("gemma_model", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchJob: Job? = null

    private val _status = MutableStateFlow(currentStatus())
    override val status: StateFlow<ModelStatus> = _status.asStateFlow()

    init {
        pendingDownloadId()?.let { watch(it, expectedSha256 = prefs.getString(KEY_SHA, null)) }
    }

    /** The model file if one is installed, else null. */
    fun installedFile(): File? =
        listOf(modelFile, legacyFile).firstOrNull { it.isFile && it.length() > MIN_MODEL_BYTES }

    override suspend fun download(hfToken: String) {
        if (_status.value.isBusy) return
        _status.value = ModelStatus.Downloading(0, null)
        try {
            val resolved = withContext(Dispatchers.IO) { resolveModelDownload(hfToken, ::httpHead) }
            dir.mkdirs()
            partialFile.delete()
            val request = DownloadManager.Request(Uri.parse(resolved.url))
                .setTitle("Gemma model for Weather")
                .setDescription(GemmaModelSource.FILE_NAME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, MODELS_DIR, PARTIAL_NAME)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            val id = downloads.enqueue(request)
            prefs.edit().putLong(KEY_ID, id).putString(KEY_SHA, resolved.sha256).apply()
            _status.value = ModelStatus.Downloading(0, resolved.sizeBytes)
            watch(id, resolved.sha256)
        } catch (e: Exception) {
            _status.value = ModelStatus.Failed(e.message ?: "Couldn't start the download")
        }
    }

    override fun cancelDownload() {
        watchJob?.cancel()
        pendingDownloadId()?.let { downloads.remove(it) }
        clearPending()
        partialFile.delete()
        _status.value = currentStatus()
    }

    override suspend fun import(uri: String) {
        if (_status.value.isBusy) return
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
        legacyFile.delete()
        _status.value = currentStatus()
    }

    /** Polls DownloadManager for progress until the download finishes, then verifies and installs it. */
    private fun watch(id: Long, expectedSha256: String?) {
        watchJob?.cancel()
        watchJob = scope.launch {
            while (isActive) {
                val info = query(id)
                if (info == null) { // Removed from the system list (e.g. cleared by the user).
                    clearPending()
                    _status.value = currentStatus()
                    return@launch
                }
                when (info.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        finishDownload(expectedSha256)
                        downloads.remove(id) // Forget the entry; the file has already been moved.
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        downloads.remove(id)
                        clearPending()
                        partialFile.delete()
                        _status.value = ModelStatus.Failed(failureMessage(info.reason))
                        return@launch
                    }
                    else -> _status.value = ModelStatus.Downloading(
                        downloadedBytes = info.downloaded.coerceAtLeast(0),
                        totalBytes = info.total.takeIf { it > 0 },
                        waitingForNetwork = info.status == DownloadManager.STATUS_PAUSED,
                    )
                }
                delay(POLL_MS)
            }
        }
    }

    private fun finishDownload(expectedSha256: String?) {
        _status.value = ModelStatus.Verifying
        clearPending()
        if (expectedSha256 != null && !sha256(partialFile).equals(expectedSha256, ignoreCase = true)) {
            partialFile.delete()
            _status.value = ModelStatus.Failed("The download was corrupted. Please try again.")
            return
        }
        install(partialFile)
        _status.value = currentStatus()
    }

    private fun copyIn(uri: Uri) {
        val name = displayName(uri)
        require(name == null || name.endsWith(".task", ignoreCase = true)) {
            "Pick a MediaPipe .task model file (e.g. ${GemmaModelSource.FILE_NAME})"
        }
        dir.mkdirs()
        // Copy to a temp file first so a failed or partial import never replaces a working model.
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Couldn't open the selected file" }
            partialFile.outputStream().use { input.copyTo(it, bufferSize = 1 shl 20) }
        }
        check(partialFile.length() > MIN_MODEL_BYTES) { "That file is too small to be a Gemma model" }
        install(partialFile)
    }

    private fun install(file: File) {
        modelFile.delete()
        legacyFile.delete()
        check(file.renameTo(modelFile)) { "Couldn't save the model" }
    }

    private data class DownloadInfo(val status: Int, val reason: Int, val downloaded: Long, val total: Long)

    private fun query(id: Long): DownloadInfo? =
        downloads.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return null
            DownloadInfo(
                status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                downloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            )
        }

    private fun failureMessage(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough free space. Gemma needs about 600 MB."
        DownloadManager.ERROR_CANNOT_RESUME, DownloadManager.ERROR_HTTP_DATA_ERROR ->
            "The download was interrupted. Please try again."
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage isn't available right now"
        in 400..599 -> "The download link expired or was refused (HTTP $reason). Please try again."
        else -> "The download failed. Please try again."
    }

    private fun pendingDownloadId(): Long? = prefs.getLong(KEY_ID, -1).takeIf { it >= 0 }

    private fun clearPending() = prefs.edit().remove(KEY_ID).remove(KEY_SHA).apply()

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun currentStatus(): ModelStatus =
        installedFile()?.let { ModelStatus.Installed(it.length()) } ?: ModelStatus.NotInstalled

    private companion object {
        const val MODELS_DIR = "models"
        const val PARTIAL_NAME = "gemma.task.part"
        const val MIN_MODEL_BYTES = 10L * 1024 * 1024
        const val POLL_MS = 500L
        const val KEY_ID = "download_id"
        const val KEY_SHA = "download_sha256"

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
