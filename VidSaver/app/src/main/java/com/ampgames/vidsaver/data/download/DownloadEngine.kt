package com.ampgames.vidsaver.data.download

import android.content.Context
import com.ampgames.vidsaver.core.net.NetworkMonitor
import com.ampgames.vidsaver.di.ApplicationScope
import com.ampgames.vidsaver.di.IoDispatcher
import com.ampgames.vidsaver.domain.download.DownloadFailure
import com.ampgames.vidsaver.domain.download.DownloadStatus
import com.ampgames.vidsaver.domain.download.ProgressThrottle
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Something worth reacting to elsewhere (notifications now, ads in Phase 6). */
sealed interface DownloadEvent {
    data class Completed(val id: Long, val fileName: String) : DownloadEvent
    data class Failed(val id: Long, val fileName: String, val failure: DownloadFailure) : DownloadEvent
    data class RetryScheduled(val id: Long, val attempt: Int) : DownloadEvent
}

/**
 * Runs the transfers.
 *
 * At most [MAX_CONCURRENT] downloads run at once; the rest wait in the queue.
 * Each one writes to an app-private `.part` file and is only moved into the
 * user's media library once it is complete, so a killed process can never leave
 * a truncated video in the gallery.
 *
 * The engine is process-scoped, not tied to the service: the service keeps the
 * process alive and shows the notification, but cancelling a job and updating
 * the database happen here.
 */
@Singleton
class DownloadEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository,
    private val strategySelector: DownloadStrategySelector,
    private val mediaStorePublisher: MediaStorePublisher,
    private val preferences: DownloadPreferences,
    private val networkMonitor: NetworkMonitor,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val semaphore = Semaphore(MAX_CONCURRENT)
    private val jobs = ConcurrentHashMap<Long, Job>()

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    /** Ids currently being transferred, for the notification and the UI. */
    val runningIds: Set<Long> get() = jobs.keys

    /**
     * Starts every queued download, up to the concurrency limit. Safe to call
     * repeatedly — a download already running is not started twice.
     */
    fun pump() {
        scope.launch {
            val settings = preferences.current()
            if (!networkMonitor.allowsDownload(settings.wifiOnly)) {
                Timber.d("Holding the queue: network does not meet the current preference")
                return@launch
            }
            repository.nextQueued(MAX_CONCURRENT).forEach { entity -> launchJob(entity.id) }
        }
    }

    fun start(id: Long) {
        scope.launch {
            repository.setStatus(id, DownloadStatus.QUEUED)
            launchJob(id)
        }
    }

    fun pause(id: Long) {
        jobs.remove(id)?.cancel(PauseCancellation)
        scope.launch { repository.setStatus(id, DownloadStatus.PAUSED) }
    }

    fun resume(id: Long) {
        scope.launch {
            repository.requeue(id)
            launchJob(id)
        }
    }

    fun cancel(id: Long) {
        jobs.remove(id)?.cancel(CancelCancellation)
        scope.launch {
            repository.cancel(id)
            partFile(id).delete()
            pump()
        }
    }

    fun pauseAll() {
        jobs.keys.toList().forEach { pause(it) }
    }

    fun cancelAll() {
        jobs.keys.toList().forEach { cancel(it) }
    }

    /** Removes a download and, when it finished, the file it produced. */
    fun deleteWithFile(id: Long) {
        jobs.remove(id)?.cancel(CancelCancellation)
        scope.launch {
            val entity = repository.find(id)
            entity?.mediaStoreUri?.let { mediaStorePublisher.delete(it) }
            partFile(id).delete()
            repository.delete(id)
        }
    }

    private fun launchJob(id: Long) {
        // putIfAbsent keeps a double tap (or a retry racing the queue pump) from
        // starting the same download twice.
        val job = scope.launch(start = CoroutineStart.LAZY) { runDownload(id) }
        if (jobs.putIfAbsent(id, job) != null) {
            job.cancel()
            return
        }
        job.invokeOnCompletion { jobs.remove(id, job) }
        job.start()
    }

    private suspend fun runDownload(id: Long) {
        val entity = repository.find(id) ?: return
        if (entity.statusEnum == DownloadStatus.CANCELLED) return

        semaphore.withPermit {
            // Status may have changed while we waited for a slot. Returns here
            // are label-scoped so the trailing pump() still runs and the queue
            // keeps moving.
            val current = repository.find(id) ?: return@withPermit
            if (current.statusEnum == DownloadStatus.CANCELLED) return@withPermit

            repository.setStatus(id, DownloadStatus.RUNNING)
            val candidate = current.toCandidate(repository.decodeHeaders(current))
            val strategy = strategySelector.strategyFor(candidate)

            if (strategy == null) {
                finishWithFailure(
                    id,
                    current.fileName,
                    DownloadFailure.UNSUPPORTED,
                    "No download strategy for ${current.mediaType}",
                )
                return@withPermit
            }

            val target = partFile(id)
            val throttle = ProgressThrottle(clock = System::currentTimeMillis)

            val result = strategy.download(candidate, target) { progress ->
                throttle.onProgress(progress.bytesDownloaded)?.let { sample ->
                    // Fire-and-forget: progress writes must never slow the transfer.
                    scope.launch {
                        repository.updateProgress(
                            id = id,
                            bytesDownloaded = sample.bytesDownloaded,
                            totalBytes = progress.totalBytes,
                            speed = sample.speedBytesPerSecond,
                        )
                    }
                }
            }

            result.fold(
                onSuccess = { file -> publish(id, current.fileName, current.mimeType, file) },
                onFailure = { error -> handleFailure(id, current.fileName, error) },
            )
        }

        pump()
    }

    private suspend fun publish(id: Long, fileName: String, mimeType: String?, file: File) {
        // The download itself is done; do not let cancellation strand the bytes
        // in app-private storage where the user can never reach them.
        withContext(NonCancellable) {
            mediaStorePublisher.publish(file, fileName, mimeType).fold(
                onSuccess = { published ->
                    repository.markCompleted(id, published.uri, published.sizeBytes)
                    Timber.i("Download %d complete: %s", id, fileName)
                    _events.tryEmit(DownloadEvent.Completed(id, fileName))
                },
                onFailure = { error ->
                    finishWithFailure(
                        id,
                        fileName,
                        DownloadFailure.STORAGE,
                        error.message ?: "Could not save the file",
                    )
                },
            )
        }
    }

    private suspend fun handleFailure(id: Long, fileName: String, error: Throwable) {
        if (error is CancellationException) {
            // Pause and cancel already wrote the right status.
            Timber.d("Download %d stopped: %s", id, error.message)
            return
        }

        val failure = error.toFailure()
        val retryable = failure.isRetryable && repository.hasRetriesLeft(id)

        repository.markFailed(id, failure, error.message, willRetry = retryable)

        if (retryable) {
            val attempt = (repository.find(id)?.retryCount ?: 1)
            Timber.w(error, "Download %d failed, retry %d scheduled", id, attempt)
            // WorkManager owns the backoff and the network constraint; the
            // worker only flips rows back to QUEUED and restarts the service.
            DownloadRetryWorker.schedule(context, preferences.current().wifiOnly)
            _events.tryEmit(DownloadEvent.RetryScheduled(id, attempt))
        } else {
            Timber.w(error, "Download %d failed permanently", id)
            _events.tryEmit(DownloadEvent.Failed(id, fileName, failure))
        }
    }

    private suspend fun finishWithFailure(
        id: Long,
        fileName: String,
        failure: DownloadFailure,
        message: String,
    ) {
        repository.markFailed(id, failure, message, willRetry = false)
        _events.tryEmit(DownloadEvent.Failed(id, fileName, failure))
    }

    /** Restores a sane state after a process death. */
    suspend fun recoverAfterProcessDeath() = withContext(ioDispatcher) {
        repository.resetInterrupted()
    }

    private fun partFile(id: Long): File =
        File(partDirectory(), "$id.part")

    private fun partDirectory(): File =
        File(context.filesDir, PART_DIRECTORY).apply { mkdirs() }

    private fun Throwable.toFailure(): DownloadFailure = when (this) {
        is DownloadError.Encrypted -> DownloadFailure.ENCRYPTED
        is DownloadError.LiveStream -> DownloadFailure.LIVE_STREAM
        is DownloadError.Unsupported -> DownloadFailure.UNSUPPORTED
        is DownloadError.Http -> DownloadFailure.HTTP
        is DownloadError.Network -> DownloadFailure.NETWORK
        is java.io.IOException -> DownloadFailure.NETWORK
        else -> DownloadFailure.UNKNOWN
    }

    private object PauseCancellation : CancellationException("Paused by the user")
    private object CancelCancellation : CancellationException("Cancelled by the user")

    companion object {
        /** Free-tier concurrency. Premium raises this in Phase 5. */
        const val MAX_CONCURRENT = 3

        private const val PART_DIRECTORY = "downloads"
    }
}
