package com.ampgames.vidsaver.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ampgames.vidsaver.data.player.PlaybackService
import com.google.common.util.concurrent.MoreExecutors
import timber.log.Timber

/**
 * Connects to [PlaybackService] and exposes its [MediaController], releasing it
 * when the composable leaves.
 *
 * The controller is what makes the player outlive the screen: the ExoPlayer
 * lives in the service, so rotation, Picture-in-Picture and background audio do
 * not interrupt playback.
 */
@UnstableApi
@Composable
fun rememberMediaController(): State<MediaController?> {
    val context = LocalContext.current
    val controllerState = remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(context) {
        val token = SessionToken(
            context.applicationContext,
            ComponentName(context.applicationContext, PlaybackService::class.java),
        )
        val future = MediaController.Builder(context.applicationContext, token).buildAsync()

        future.addListener(
            {
                controllerState.value = runCatching { future.get() }
                    .onFailure { Timber.e(it, "Could not connect to the playback service") }
                    .getOrNull()
            },
            MoreExecutors.directExecutor(),
        )

        onDispose {
            controllerState.value?.release()
            controllerState.value = null
            MediaController.releaseFuture(future)
        }
    }

    return controllerState
}
