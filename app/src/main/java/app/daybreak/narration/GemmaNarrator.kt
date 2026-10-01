package app.daybreak.narration

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.PromptTemplates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs Gemma on the device with MediaPipe LLM Inference. Nothing leaves the phone.
 *
 * The engine (model weights, a few hundred MB) is loaded lazily on first use and kept for follow-up calls, then
 * released after [idleReleaseMs] without a generation, on [release] (e.g. when the model is removed) or on [close].
 * It's reloaded if the model file changes. One generation runs at a time.
 */
class GemmaNarrator(
    private val context: Context,
    private val modelFile: () -> File?,
    private val timeoutMs: Long = 30_000,
    private val idleReleaseMs: Long = 5 * 60_000,
) : TextGenerator, AutoCloseable {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var idleJob: Job? = null
    private var engine: LlmInference? = null
    private var engineKey: String? = null

    override suspend fun generate(prompt: String, temperature: Float, seed: Int): String = mutex.withLock {
        idleJob?.cancel()
        try {
            generateLocked(prompt, temperature, seed)
        } finally {
            idleJob = scope.launch {
                delay(idleReleaseMs)
                mutex.withLock { release() }
            }
        }
    }

    /** Frees the engine now (it's loaded again on the next generation). */
    fun releaseEngine() {
        scope.launch { mutex.withLock { release() } }
    }

    /** Frees the engine and stops the idle timer; the narrator can't be used afterwards. */
    override fun close() {
        scope.launch {
            mutex.withLock { release() }
        }.invokeOnCompletion { scope.cancel() }
    }

    private suspend fun generateLocked(prompt: String, temperature: Float, seed: Int): String =
        withContext(Dispatchers.IO) {
            val session = LlmInferenceSession.createFromOptions(engineFor(modelFile()), sessionOptions(temperature, seed))
            try {
                session.addQueryChunk(prompt)
                // A timeout is an ordinary failure (the hand-written meme stays), not a cancellation of the caller.
                withTimeoutOrNull(timeoutMs) { session.awaitResponse() }
                    ?: throw IOException("Gemma took longer than ${timeoutMs / 1000}s")
            } finally {
                session.close()
            }
        }

    private fun engineFor(file: File?): LlmInference {
        if (file == null) {
            release()
            error("No Gemma model imported")
        }
        val key = "${file.path}:${file.length()}:${file.lastModified()}"
        engine?.takeIf { engineKey == key }?.let { return it }
        release()
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(file.path)
            .setMaxTokens(MAX_TOKENS)
            .setPreferredBackend(LlmInference.Backend.CPU) // Works on every device; GPU support varies.
            .build()
        return LlmInference.createFromOptions(context, options).also {
            engine = it
            engineKey = key
        }
    }

    private fun release() {
        runCatching { engine?.close() } // never let a native close failure crash a background release
        engine = null
        engineKey = null
    }

    private fun sessionOptions(temperature: Float, seed: Int) = LlmInferenceSession.LlmInferenceSessionOptions.builder()
        .setTemperature(temperature)
        .setTopK(if (temperature > 0.5f) 40 else 20)
        .setRandomSeed(seed)
        .setPromptTemplates(
            PromptTemplates.builder()
                .setUserPrefix("<start_of_turn>user\n")
                .setUserSuffix("<end_of_turn>\n")
                .setModelPrefix("<start_of_turn>model\n")
                .setModelSuffix("<end_of_turn>\n")
                .build()
        )
        .build()

    private suspend fun LlmInferenceSession.awaitResponse(): String = suspendCancellableCoroutine { cont ->
        val future = generateResponseAsync()
        cont.invokeOnCancellation { runCatching { cancelGenerateResponseAsync() } }
        future.addListener({
            try {
                cont.resume(future.get())
            } catch (e: ExecutionException) {
                cont.resumeWithException(e.cause ?: e)
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }, Runnable::run)
    }

    private companion object {
        /** Prompt + response budget: the meme prompt and its two-line caption fit with plenty to spare. */
        const val MAX_TOKENS = 1024
    }
}
