package com.ampgames.vidsaver.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.ampgames.vidsaver.MainActivity
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.core.format.ByteFormat
import com.ampgames.vidsaver.data.download.db.DownloadEntity
import com.ampgames.vidsaver.domain.download.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the foreground-service notification for downloads.
 *
 * One ongoing notification summarises the queue rather than one per download:
 * three simultaneous downloads should not mean three notifications the user has
 * to dismiss. Completion posts a separate, dismissible notification.
 */
@Singleton
class DownloadNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val manager: NotificationManager? get() = context.getSystemService()

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = manager ?: return

        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                context.getString(R.string.notif_channel_progress),
                // LOW: an ongoing progress bar should never make a sound.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notif_channel_progress_desc)
                setShowBadge(false)
            },
        )

        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_COMPLETE,
                context.getString(R.string.notif_channel_complete),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notif_channel_complete_desc)
            },
        )
    }

    /** Ongoing notification for the foreground service while work is in flight. */
    fun buildProgressNotification(active: List<DownloadEntity>): Notification {
        val running = active.filter { it.statusEnum == DownloadStatus.RUNNING }
        val primary = running.firstOrNull() ?: active.firstOrNull()

        val title = when {
            primary == null -> context.getString(R.string.notif_downloads_working)
            active.size == 1 -> primary.fileName
            else -> context.getString(R.string.notif_downloads_count, active.size)
        }

        val totalBytes = active.sumOf { it.totalBytes ?: 0L }
        val doneBytes = active.sumOf { it.bytesDownloaded }
        val determinate = totalBytes > 0

        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(statusLine(active))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent())
            .setProgress(
                if (determinate) 100 else 0,
                if (determinate) ((doneBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0,
                !determinate,
            )

        builder.addAction(
            0,
            context.getString(R.string.notif_action_pause),
            serviceAction(DownloadService.ACTION_PAUSE_ALL),
        )
        builder.addAction(
            0,
            context.getString(R.string.notif_action_cancel),
            serviceAction(DownloadService.ACTION_CANCEL_ALL),
        )

        return builder.build()
    }

    /**
     * Posted as the foreground service stops with downloads still paused, so the
     * user keeps a way to resume from the shade. Dismissible, and not ongoing:
     * a foreground service must not stay alive for work that is not running.
     */
    fun showPaused(count: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.notif_downloads_paused))
            .setContentText(context.resources.getQuantityString(R.plurals.notif_paused_count, count, count))
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())
            .addAction(
                0,
                context.getString(R.string.notif_action_resume),
                serviceAction(DownloadService.ACTION_RESUME_ALL),
            )
            .build()
        notify(PAUSED_NOTIFICATION_ID, notification)
    }

    fun clearPaused() {
        runCatching { manager?.cancel(PAUSED_NOTIFICATION_ID) }
    }

    fun showCompleted(fileName: String, id: Long) {
        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.notif_download_complete))
            .setContentText(fileName)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        notify(COMPLETE_NOTIFICATION_BASE + id.toInt(), notification)
    }

    fun showFailed(fileName: String, reason: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETE)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.notif_download_failed))
            .setContentText("$fileName — $reason")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        notify(FAILED_NOTIFICATION_BASE + fileName.hashCode(), notification)
    }

    private fun notify(id: Int, notification: Notification) {
        // POST_NOTIFICATIONS may have been denied; posting then is a no-op we
        // must not crash on.
        runCatching { manager?.notify(id, notification) }
    }

    private fun statusLine(active: List<DownloadEntity>): String {
        val speed = active.sumOf { it.speedBytesPerSecond }
        if (speed <= 0) return context.getString(R.string.notif_downloads_working)
        val formatted = ByteFormat.speed(speed) ?: return context.getString(R.string.notif_downloads_working)
        return context.getString(R.string.notif_downloads_speed, formatted)
    }



    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, pendingIntentFlags())
    }

    private fun serviceAction(action: String): PendingIntent {
        val intent = Intent(context, DownloadService::class.java).setAction(action)
        return PendingIntent.getService(context, action.hashCode(), intent, pendingIntentFlags())
    }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    companion object {
        const val CHANNEL_PROGRESS = "downloads_progress"
        const val CHANNEL_COMPLETE = "downloads_complete"

        const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val PAUSED_NOTIFICATION_ID = 1002
        private const val COMPLETE_NOTIFICATION_BASE = 2000
        private const val FAILED_NOTIFICATION_BASE = 3000
    }
}
