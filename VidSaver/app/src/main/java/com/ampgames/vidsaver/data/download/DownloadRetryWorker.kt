package com.ampgames.vidsaver.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import timber.log.Timber

/**
 * Re-queues downloads that failed with a retryable error.
 *
 * WorkManager is used *only* for scheduling — it does not transfer anything.
 * That keeps the actual work in the foreground service where the user can see
 * and control it, while WorkManager supplies exactly what a service cannot:
 * exponential backoff, network constraints, and survival across reboots.
 */
@HiltWorker
class DownloadRetryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: DownloadRepository,
    private val preferences: DownloadPreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val pending = runCatching { repository.pendingRetries() }.getOrElse { error ->
            Timber.e(error, "Could not read pending retries")
            return Result.retry()
        }

        if (pending.isEmpty()) {
            Timber.d("No downloads awaiting retry")
            return Result.success()
        }

        pending.forEach { entity ->
            // Back to QUEUED; the service picks it up from there.
            runCatching { repository.requeue(entity.id) }
                .onFailure { Timber.w(it, "Could not requeue download %d", entity.id) }
        }

        DownloadService.start(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "vidsaver-download-retry"

        /**
         * Schedules a retry pass. Unique work with REPLACE means ten failures do
         * not produce ten workers — one pass picks up everything pending.
         */
        fun schedule(context: Context, wifiOnly: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
                )
                .build()

            val request = OneTimeWorkRequestBuilder<DownloadRetryWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF_SECONDS, TimeUnit.SECONDS)
                .setInitialDelay(INITIAL_BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()

            runCatching {
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
            }.onFailure { Timber.w(it, "Could not schedule the retry worker") }
        }

        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME) }
        }

        private const val INITIAL_BACKOFF_SECONDS = 30L
    }
}
