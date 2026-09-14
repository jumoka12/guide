package com.ampgames.vidsaver.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.core.content.getSystemService
import timber.log.Timber

/**
 * Media volume, as a 0..1 level for the right-side drag gesture.
 *
 * Reads the real stream volume every time rather than caching: the user can
 * change it with the hardware keys while the player is open.
 */
class SystemVolume(context: Context) {

    private val audioManager: AudioManager? = context.getSystemService()

    private val maxVolume: Int
        get() = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1

    fun level(): Float {
        val manager = audioManager ?: return 0f
        return (manager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
            .coerceIn(0f, 1f)
    }

    fun set(level: Float) {
        val manager = audioManager ?: return
        val target = (level.coerceIn(0f, 1f) * maxVolume).toInt()
        runCatching {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }.onFailure { Timber.v(it, "Could not set the volume") }
    }
}

/**
 * Screen brightness for the left-side drag gesture.
 *
 * Only this window's brightness is changed, never the system setting — leaving
 * the player restores whatever the device was on, and no special permission is
 * needed.
 */
class WindowBrightness(private val activity: Activity?) {

    fun level(): Float {
        val window = activity?.window ?: return DEFAULT_LEVEL
        val current = window.attributes.screenBrightness
        // BRIGHTNESS_OVERRIDE_NONE (-1) means "follow the system"; start from the
        // middle rather than jumping when the first drag arrives.
        return if (current < 0f) DEFAULT_LEVEL else current.coerceIn(0f, 1f)
    }

    fun set(level: Float) {
        val window = activity?.window ?: return
        runCatching {
            window.attributes = window.attributes.apply {
                // A floor above zero: a fully black screen looks like a crash.
                screenBrightness = level.coerceIn(MIN_LEVEL, 1f)
            }
        }.onFailure { Timber.v(it, "Could not set the brightness") }
    }

    fun release() {
        val window = activity?.window ?: return
        runCatching {
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    private companion object {
        const val DEFAULT_LEVEL = 0.5f
        const val MIN_LEVEL = 0.02f
    }
}

/**
 * Enters Picture-in-Picture. A no-op below API 26 and on devices where the
 * system has PiP disabled, which is why every call is guarded.
 */
fun Activity.enterPipMode(aspectWidth: Int = 16, aspectHeight: Int = 9) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    runCatching {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(aspectWidth.coerceAtLeast(1), aspectHeight.coerceAtLeast(1)))
            .build()
        enterPictureInPictureMode(params)
    }.onFailure { Timber.w(it, "Could not enter Picture-in-Picture") }
}
