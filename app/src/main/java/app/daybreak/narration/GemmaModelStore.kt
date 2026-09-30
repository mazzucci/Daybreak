package app.daybreak.narration

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

sealed interface ModelStatus {
    data object NotInstalled : ModelStatus
    data object Importing : ModelStatus
    /**
     * [totalBytes] is null until the size is known. [pausedReason] is null while bytes are flowing, otherwise a
     * short human-readable reason ("Waiting for Wi-Fi").
     */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?, val pausedReason: String? = null) :
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

    /** Starts downloading the model from Hugging Face using the user's access token; returns immediately. */
    suspend fun download(hfToken: String)
    fun cancelDownload()

    /** Starts copying the model from a content:// [uri] chosen in the system file picker; returns immediately. */
    suspend fun import(uri: String)
    fun remove()
}

/**
 * Keeps a single Gemma `.task` file in app-specific storage (no storage permission needed; removed on uninstall).
 *
 * Downloads go through the system DownloadManager, so they continue in the background with a notification and
 * survive the app being closed; an unfinished download is picked up again on the next launch.
 *
 * There is one instance per process ([get]) so only one watcher ever follows a download. All file and
 * DownloadManager work runs on a single background thread, in submission order, so a cancel's cleanup can't
 * interleave with the next download. Every status write carries the generation it belongs to; anything from a
 * cancelled or superseded operation is dropped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GemmaModelStore private constructor(private val context: Context) : LocalModelManager {
    private val internalDir = File(context.filesDir, MODELS_DIR)
    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences("gemma_model", Context.MODE_PRIVATE)

    // A download left running by an earlier launch counts as busy right away, so it can't be overtaken.
    private val _status = MutableStateFlow(
        if (pendingId() != null) ModelStatus.Downloading(0, prefs.getLong(KEY_SIZE, -1).takeIf { it > 0 })
        else currentStatus()
    )
    override val status: StateFlow<ModelStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(1) +
            // Backstop only; operations report their own failures. Unconditional so nothing can stay stuck "busy".
            CoroutineExceptionHandler { _, e -> _status.value = ModelStatus.Failed(e.message ?: "Something went wrong") }
    )
    private var generation = 0L // guarded by `this`
    private var job: Job? = null // guarded by `this`

    init {
        val gen = generation // read now: a cancel before the coroutine starts must supersede the resume
        scope.launch { resumePending(gen) }
    }

    /** The model file if one is installed, else null. */
    fun installedFile(): File? =
        listOfNotNull(externalDir(), internalDir)
            .map { File(it, MODEL_NAME) }
            .firstOrNull { it.isFile && it.length() > MIN_MODEL_BYTES }

    override suspend fun download(hfToken: String) = start(ModelStatus.Downloading(0, null)) { gen ->
        val resolved = resolveModelDownload(hfToken, ::httpHead)
        val dir = externalDir() ?: throw ModelDownloadException("Storage isn't available right now. Try again later.")
        if (!isCurrent(gen)) return@start // cancelled while asking Hugging Face
        File(dir, PARTIAL_NAME).delete()
        val request = DownloadManager.Request(Uri.parse(resolved.url))
            .setTitle("Gemma model for Weather")
            .setDescription(GemmaModelSource.FILE_NAME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, MODELS_DIR, PARTIAL_NAME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val id = downloads.enqueue(request)
        prefs.edit()
            .putLong(KEY_ID, id)
            .putString(KEY_SHA, resolved.sha256)
            .putLong(KEY_SIZE, resolved.sizeBytes ?: -1)
            .commit()
        setStatus(gen, ModelStatus.Downloading(0, resolved.sizeBytes))
        watch(gen, id)
    }

    override fun cancelDownload() {
        synchronized(this) {
            generation++
            job?.cancel()
            _status.value = currentStatus()
        }
        // Queued behind whatever the cancelled job was doing, and ahead of any download started after this.
        scope.launch {
            abandonPending()
            synchronized(this@GemmaModelStore) { if (!_status.value.isBusy) _status.value = currentStatus() }
        }
    }

    override suspend fun import(uri: String) = start(ModelStatus.Importing) { gen ->
        copyIn(Uri.parse(uri))
        setStatus(gen, currentStatus())
    }

    override fun remove() {
        scope.launch {
            listOfNotNull(externalDir(), internalDir).forEach { File(it, MODEL_NAME).delete() }
            synchronized(this@GemmaModelStore) { if (!_status.value.isBusy) _status.value = currentStatus() }
        }
    }

    /** Runs [block] as the new current operation unless one is already in progress. */
    private fun start(initial: ModelStatus, block: suspend (gen: Long) -> Unit) {
        synchronized(this) {
            if (_status.value.isBusy) return
            val gen = ++generation
            _status.value = initial
            job = scope.launch {
                try {
                    block(gen)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (initial is ModelStatus.Downloading) abandonPending()
                    setStatus(gen, ModelStatus.Failed(e.message ?: "Something went wrong"))
                }
            }
        }
    }

    /** On launch: continue following a download started earlier, or clean up after one that can't be followed. */
    private suspend fun resumePending(gen: Long) {
        val id = pendingId()
        if (id == null) {
            // Leftovers from a crash or process death during a download or import.
            listOfNotNull(externalDir(), internalDir).forEach { File(it, PARTIAL_NAME).delete() }
            return
        }
        try {
            watch(gen, id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            abandonPending()
            setStatus(gen, ModelStatus.Failed(e.message ?: "The download failed. Please try again."))
        }
    }

    /** Polls DownloadManager until the download finishes, then verifies and installs it. */
    private suspend fun watch(gen: Long, id: Long) {
        val expectedSize = prefs.getLong(KEY_SIZE, -1).takeIf { it > 0 }
        while (isCurrent(gen)) {
            val info = query(id)
            when {
                info == null -> { // Removed from the system list (e.g. cleared by the user).
                    clearPending()
                    setStatus(gen, currentStatus())
                    return
                }
                info.status == DownloadManager.STATUS_SUCCESSFUL -> {
                    finish(gen, id, info.localFile, prefs.getString(KEY_SHA, null), expectedSize)
                    return
                }
                info.status == DownloadManager.STATUS_FAILED -> {
                    abandonPending()
                    setStatus(gen, ModelStatus.Failed(failureMessage(info.reason)))
                    return
                }
                else -> setStatus(
                    gen,
                    ModelStatus.Downloading(
                        downloadedBytes = info.downloaded.coerceAtLeast(0),
                        totalBytes = info.total.takeIf { it > 0 } ?: expectedSize,
                        pausedReason = if (info.status == DownloadManager.STATUS_PAUSED) pausedReason(info.reason) else null,
                    ),
                )
            }
            delay(POLL_MS)
        }
    }

    private fun finish(gen: Long, id: Long, downloaded: File?, expectedSha256: String?, expectedSize: Long?) {
        setStatus(gen, ModelStatus.Verifying)
        val file = downloaded ?: externalDir()?.let { File(it, PARTIAL_NAME) }
        if ((file == null || !file.isFile) && installedFile() != null) {
            // Killed after installing but before forgetting the download: it's already done.
            downloads.remove(id)
            clearPending()
            setStatus(gen, currentStatus())
            return
        }
        if (file == null || !file.isFile) throw ModelDownloadException("The downloaded file is missing. Please try again.")
        val ok = when {
            expectedSha256 != null -> sha256(file).equals(expectedSha256, ignoreCase = true)
            expectedSize != null -> file.length() == expectedSize
            else -> file.length() > MIN_MODEL_BYTES
        }
        if (!isCurrent(gen)) return // cancelled while hashing; the cancel cleans up
        if (!ok) throw ModelDownloadException("The download was corrupted. Please try again.")
        install(file)
        downloads.remove(id) // The file has moved, so this only drops the system entry.
        clearPending() // Only now: if the process dies before this, the next launch verifies again.
        setStatus(gen, currentStatus())
    }

    private fun copyIn(uri: Uri) {
        val name = displayName(uri)
        require(name == null || name.endsWith(".task", ignoreCase = true)) {
            "Pick a MediaPipe .task model file (e.g. ${GemmaModelSource.FILE_NAME})"
        }
        val dir = (externalDir() ?: internalDir).apply { mkdirs() }
        // Copy to a temp file first so a failed or partial import never replaces a working model.
        val partial = File(dir, PARTIAL_NAME)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Couldn't open the selected file" }
                partial.outputStream().use { input.copyTo(it, bufferSize = 1 shl 20) }
            }
            check(partial.length() > MIN_MODEL_BYTES) { "That file is too small to be a Gemma model" }
            install(partial)
        } finally {
            partial.delete()
        }
    }

    /** Moves [file] to the model name in its own directory (same filesystem) and removes any other copy. */
    private fun install(file: File) {
        val target = File(file.parentFile, MODEL_NAME)
        listOfNotNull(externalDir(), internalDir).map { File(it, MODEL_NAME) }.forEach { it.delete() }
        check(file.renameTo(target)) { "Couldn't save the model" }
    }

    /** Removes the pending DownloadManager entry (and its partial file) if there is one. */
    private fun abandonPending() {
        pendingId()?.let { runCatching { downloads.remove(it) } }
        clearPending()
        externalDir()?.let { File(it, PARTIAL_NAME).delete() }
    }

    private fun isCurrent(gen: Long) = synchronized(this) { gen == generation }

    private fun setStatus(gen: Long, status: ModelStatus) = synchronized(this) {
        if (gen == generation) _status.value = status
    }

    private data class DownloadInfo(
        val status: Int,
        val reason: Int,
        val downloaded: Long,
        val total: Long,
        val localFile: File?,
    )

    private fun query(id: Long): DownloadInfo? =
        downloads.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return null
            DownloadInfo(
                status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                downloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                localFile = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    ?.let { Uri.parse(it).path }
                    ?.let(::File),
            )
        }

    private fun pausedReason(reason: Int): String = when (reason) {
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for a network connection"
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi"
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "Connection interrupted, retrying shortly"
        else -> "Paused"
    }

    private fun failureMessage(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough free space. Gemma needs about 600 MB."
        DownloadManager.ERROR_CANNOT_RESUME, DownloadManager.ERROR_HTTP_DATA_ERROR ->
            "The download was interrupted. Please try again."
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage isn't available right now"
        in 400..599 -> "The download link expired or was refused (HTTP $reason). Please try again."
        else -> "The download failed. Please try again."
    }

    private fun externalDir(): File? = context.getExternalFilesDir(MODELS_DIR)

    private fun pendingId(): Long? = prefs.getLong(KEY_ID, -1).takeIf { it >= 0 }

    private fun clearPending() = prefs.edit().remove(KEY_ID).remove(KEY_SHA).remove(KEY_SIZE).commit()

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun currentStatus(): ModelStatus =
        installedFile()?.let { ModelStatus.Installed(it.length()) } ?: ModelStatus.NotInstalled

    companion object {
        private const val MODELS_DIR = "models"
        private const val MODEL_NAME = "gemma.task"
        private const val PARTIAL_NAME = "gemma.task.part"
        private const val MIN_MODEL_BYTES = 10L * 1024 * 1024
        private const val POLL_MS = 500L
        private const val KEY_ID = "download_id"
        private const val KEY_SHA = "download_sha256"
        private const val KEY_SIZE = "download_size"

        @Volatile private var instance: GemmaModelStore? = null

        /** The process-wide store. */
        fun get(context: Context): GemmaModelStore =
            instance ?: synchronized(this) {
                instance ?: GemmaModelStore(context.applicationContext).also { instance = it }
            }

        private fun sha256(file: File): String {
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
