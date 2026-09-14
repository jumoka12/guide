package com.ampgames.vidsaver.data.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ampgames.vidsaver.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns the ExoPlayer instance and publishes it as a [MediaSession].
 *
 * Holding the player in a service rather than the screen is what makes
 * background audio, the system media notification, lock-screen controls and
 * Picture-in-Picture work: the UI attaches and detaches, playback does not stop
 * just because the Activity went away.
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var preferences: PlayerPreferences

    private val serviceScope = CoroutineScope(SupervisorJob())
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                // Request audio focus and duck for other apps; a video player
                // that talks over a phone call is a bug report.
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * The user swiped the app away. With background audio off this is the point
     * to stop; with it on, playback continues and the media notification stays.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        serviceScope.launch {
            val backgroundAudio = runCatching { preferences.settings.first().backgroundAudio }
                .getOrDefault(false)
            val player = mediaSession?.player

            if (!backgroundAudio || player == null || !player.isPlaying) {
                Timber.d("Task removed and background audio is off; stopping playback")
                player?.pause()
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
