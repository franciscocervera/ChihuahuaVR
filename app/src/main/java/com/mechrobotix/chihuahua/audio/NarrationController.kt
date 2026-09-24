package com.mechrobotix.chihuahua.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mechrobotix.chihuahua.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Estado visible de la reproducción de narraciones locales. */
enum class NarrationStatus {
    IDLE,
    PREPARING,
    READY,
    PLAYING,
    FILE_MISSING,
    PLAYBACK_ERROR,
}

class NarrationController(
    context: Context,
    private val onPlaybackStarted: () -> Unit,
    private val onPlaybackFinished: () -> Unit,
    private val onStatusChanged: (NarrationStatus) -> Unit,
    private val onProgressChanged: (NarrationProgress) -> Unit,
) {
    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var progressRunnable: Runnable? = null
    private var playbackGeneration = 0
    private var closed = false
    private var lastPublishedStatus = NarrationStatus.IDLE
    private var lastPublishedProgress = NarrationProgress()
    private val _events = MutableSharedFlow<NarrationEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<NarrationEvent> = _events.asSharedFlow()

    fun play(fileName: String) {
        if (closed) return
        runOnMain { playOnMain(fileName) }
    }

    fun stop() {
        if (closed) return
        runOnMain {
            playbackGeneration++
            releasePlayer()
            publishProgress(NarrationProgress())
            publishStatus(NarrationStatus.IDLE)
            onPlaybackFinished()
        }
    }

    fun shutdown() {
        if (closed) return
        closed = true
        runOnMain {
            playbackGeneration++
            releasePlayer()
            publishProgress(NarrationProgress())
            onPlaybackFinished()
        }
    }

    private fun playOnMain(fileName: String) {
        if (closed) return

        val normalizedFileName = fileName.trim()
        val resourceName = normalizedFileName.substringBeforeLast('.').lowercase()
        playbackGeneration++
        val generation = playbackGeneration
        releasePlayer()
        publishProgress(NarrationProgress())
        onPlaybackFinished()

        val resourceId = resolveRawResource(resourceName)
        if (resourceId == 0) {
            publishStatus(NarrationStatus.FILE_MISSING)
            _events.tryEmit(NarrationEvent.FileMissing(normalizedFileName))
            Log.w(TAG, "Narración pendiente: app/src/main/res/raw/$normalizedFileName")
            return
        }

        val activePlayer = MediaPlayer()
        player = activePlayer
        publishStatus(NarrationStatus.PREPARING)

        activePlayer.setAudioAttributes(
            capturableAudioAttributes(AudioAttributes.CONTENT_TYPE_SPEECH),
        )
        activePlayer.setOnPreparedListener { preparedPlayer ->
            if (!isCurrent(generation, preparedPlayer)) {
                preparedPlayer.release()
                return@setOnPreparedListener
            }
            val duration = preparedPlayer.duration.coerceAtLeast(0)
            publishProgress(NarrationProgress(durationMs = duration))
            runCatching { preparedPlayer.start() }
                .onSuccess {
                    publishStatus(NarrationStatus.PLAYING)
                    onPlaybackStarted()
                    startProgressUpdates(generation, preparedPlayer)
                    Log.i(TAG, "Narración iniciada: $normalizedFileName")
                }
                .onFailure { error ->
                    handlePlaybackError(generation, preparedPlayer, normalizedFileName, error)
                }
        }
        activePlayer.setOnCompletionListener { completedPlayer ->
            if (!isCurrent(generation, completedPlayer)) return@setOnCompletionListener
            stopProgressUpdates()
            val duration = completedPlayer.duration.coerceAtLeast(0)
            publishProgress(NarrationProgress(positionMs = duration, durationMs = duration))
            player = null
            completedPlayer.release()
            publishStatus(NarrationStatus.READY)
            onPlaybackFinished()
            _events.tryEmit(NarrationEvent.Completed(normalizedFileName))
            Log.i(TAG, "Narración finalizada: $normalizedFileName")
        }
        activePlayer.setOnErrorListener { failedPlayer, what, extra ->
            if (isCurrent(generation, failedPlayer)) {
                handlePlaybackError(
                    generation = generation,
                    failedPlayer = failedPlayer,
                    fileName = normalizedFileName,
                    error = IllegalStateException("MediaPlayer error what=$what extra=$extra"),
                )
            }
            true
        }

        runCatching {
            val resourceUri = Uri.parse(
                "android.resource://${applicationContext.packageName}/$resourceId",
            )
            activePlayer.setDataSource(applicationContext, resourceUri)
            activePlayer.prepareAsync()
        }.onFailure { error ->
            handlePlaybackError(generation, activePlayer, normalizedFileName, error)
        }
    }

    private fun startProgressUpdates(generation: Int, activePlayer: MediaPlayer) {
        stopProgressUpdates()
        val update = object : Runnable {
            override fun run() {
                if (!isCurrent(generation, activePlayer)) return
                val duration = runCatching { activePlayer.duration }.getOrDefault(0).coerceAtLeast(0)
                val position = runCatching { activePlayer.currentPosition }.getOrDefault(0)
                    .coerceIn(0, duration.coerceAtLeast(0))
                publishProgress(NarrationProgress(position, duration))
                if (runCatching { activePlayer.isPlaying }.getOrDefault(false)) {
                    mainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
                }
            }
        }
        progressRunnable = update
        mainHandler.post(update)
    }

    private fun stopProgressUpdates() {
        progressRunnable?.let(mainHandler::removeCallbacks)
        progressRunnable = null
    }

    private fun resolveRawResource(resourceName: String): Int {
        val reflectedId = runCatching {
            R.raw::class.java.getField(resourceName).getInt(null)
        }.getOrDefault(0)
        if (reflectedId != 0) return reflectedId

        return listOf(
            applicationContext.packageName,
            R::class.java.packageName,
        ).distinct().firstNotNullOfOrNull { resourcePackage ->
            applicationContext.resources
                .getIdentifier(resourceName, "raw", resourcePackage)
                .takeIf { it != 0 }
        } ?: 0
    }

    private fun handlePlaybackError(
        generation: Int,
        failedPlayer: MediaPlayer,
        fileName: String,
        error: Throwable,
    ) {
        if (!isCurrent(generation, failedPlayer)) return
        stopProgressUpdates()
        player = null
        runCatching { failedPlayer.reset() }
        failedPlayer.release()
        publishProgress(NarrationProgress())
        publishStatus(NarrationStatus.PLAYBACK_ERROR)
        onPlaybackFinished()
        _events.tryEmit(NarrationEvent.PlaybackError(fileName))
        Log.e(TAG, "No se pudo reproducir $fileName", error)
    }

    private fun releasePlayer() {
        stopProgressUpdates()
        val activePlayer = player ?: return
        player = null
        activePlayer.setOnPreparedListener(null)
        activePlayer.setOnCompletionListener(null)
        activePlayer.setOnErrorListener(null)
        runCatching { activePlayer.stop() }
        runCatching { activePlayer.reset() }
        activePlayer.release()
    }

    private fun isCurrent(generation: Int, candidate: MediaPlayer): Boolean =
        !closed && generation == playbackGeneration && player === candidate

    private fun publishStatus(status: NarrationStatus) {
        if (lastPublishedStatus == status) return
        lastPublishedStatus = status
        onStatusChanged(status)
    }

    private fun publishProgress(progress: NarrationProgress) {
        if (lastPublishedProgress == progress) return
        lastPublishedProgress = progress
        onProgressChanged(progress)
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    companion object {
        private const val TAG = "NarrationController"
        private const val PROGRESS_UPDATE_INTERVAL_MS = 120L
    }
}
