package com.ampgames.vidsaver.data.download

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.domain.download.DownloadFailure
import com.ampgames.vidsaver.domain.download.DownloadStatus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Keeps the process alive while downloads run and shows the ongoing progress
 * notification.
 *
 * The service owns no download state: [DownloadEngine] does the transferring and
 * this observes the repository. So a download that finishes while the service is
 * stopping still lands in the database correctly.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var engine: DownloadEngine

    @Inject lateinit var repository: DownloadRepository

    @Inject lateinit var notifications: DownloadNotifications

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)
    private var observerJob: Job? = null
    private var startedForeground = false

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannels()
        observeDownloads()
        observeEvents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground immediately: Android gives a started service only
        // a few seconds to call startForeground before it kills the process.
        promoteToForeground()

        when (intent?.action) {
            ACTION_PAUSE_ALL -> engine.pauseAll()
            ACTION_RESUME_ALL -> resumeAll()
            ACTION_CANCEL_ALL -> engine.cancelAll()
            ACTION_PAUSE -> intent.downloadId()?.let(engine::pause)
            ACTION_RESUME -> intent.downloadId()?.let(engine::resume)
            ACTION_CANCEL -> intent.downloadId()?.let(engine::cancel)
            else -> engine.pump()
        }

        // START_STICKY would restart with a null intent and no queue context;
        // recovery is handled explicitly at app start instead.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun promoteToForeground() {
        if (startedForeground) return
        updateForeground(notifications.buildProgressNotification(emptyList()))
    }

    private fun observeDownloads() {
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            repository.observeActive().collectLatest { tracked ->
                val working = tracked.filter { it.statusEnum.isActive }

                if (working.isEmpty()) {
                    // A foreground service must not stay alive for work that is
                    // not running. Paused downloads hand off to a plain,
                    // dismissible notification that still offers Resume.
                    val paused = tracked.count { it.statusEnum == DownloadStatus.PAUSED }
                    if (paused > 0) notifications.showPaused(paused) else notifications.clearPaused()
                    stopSelfSafely()
                    return@collectLatest
                }

                notifications.clearPaused()
                runCatching { notifications.buildProgressNotification(working) }
                    .onSuccess { notification -> updateForeground(notification) }
            }
        }
    }

    private fun updateForeground(notification: Notification) {
        runCatching {
            ServiceCompat.startForeground(
                this,
                DownloadNotifications.FOREGROUND_NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                },
            )
            startedForeground = true
        }.onFailure { Timber.w(it, "Could not update the download notification") }
    }

    private fun observeEvents() {
        serviceScope.launch {
            engine.events.collect { event ->
                when (event) {
                    is DownloadEvent.Completed -> notifications.showCompleted(event.fileName, event.id)
                    is DownloadEvent.Failed ->
                        notifications.showFailed(event.fileName, describe(event.failure))
                    is DownloadEvent.RetryScheduled -> Unit // the progress notification covers it
                }
            }
        }
    }

    private fun resumeAll() {
        serviceScope.launch {
            repository.resumable().forEach { engine.resume(it.id) }
        }
    }

    private fun describe(failure: DownloadFailure): String = getString(
        when (failure) {
            DownloadFailure.ENCRYPTED -> R.string.download_error_encrypted
            DownloadFailure.LIVE_STREAM -> R.string.download_error_live
            DownloadFailure.NETWORK -> R.string.download_error_network
            DownloadFailure.HTTP -> R.string.download_error_http
            DownloadFailure.STORAGE -> R.string.download_error_storage
            DownloadFailure.UNSUPPORTED -> R.string.download_error_unsupported
            else -> R.string.download_error_unknown
        },
    )

    private fun stopSelfSafely() {
        startedForeground = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun Intent.downloadId(): Long? =
        getLongExtra(EXTRA_DOWNLOAD_ID, -1L).takeIf { it >= 0 }

    companion object {
        const val ACTION_PAUSE_ALL = "com.ampgames.vidsaver.action.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.ampgames.vidsaver.action.RESUME_ALL"
        const val ACTION_CANCEL_ALL = "com.ampgames.vidsaver.action.CANCEL_ALL"
        const val ACTION_PAUSE = "com.ampgames.vidsaver.action.PAUSE"
        const val ACTION_RESUME = "com.ampgames.vidsaver.action.RESUME"
        const val ACTION_CANCEL = "com.ampgames.vidsaver.action.CANCEL"

        const val EXTRA_DOWNLOAD_ID = "download_id"

        /** Starts (or nudges) the service so the queue keeps moving. */
        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Timber.w(it, "Could not start the download service") }
        }

        fun sendAction(context: Context, action: String, downloadId: Long? = null) {
            val intent = Intent(context, DownloadService::class.java).setAction(action).apply {
                downloadId?.let { putExtra(EXTRA_DOWNLOAD_ID, it) }
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Timber.w(it, "Could not send %s to the download service", action) }
        }
    }
}
