package com.ampgames.vidsaver.ui.browser.web

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Puts a page's fullscreen video on top of everything.
 *
 * A WebView only honours `requestFullscreen()` when its chrome client accepts
 * the custom view; without this, tapping the fullscreen button in any HTML5
 * player either does nothing or leaves the page painted black behind a view
 * that was never attached. The view goes straight onto the window's decor view,
 * above the Compose hierarchy, and the system bars hide until it is dismissed.
 */
class FullscreenHost {

    /** True while a custom view is attached; drives the back handler. */
    var isFullscreen by mutableStateOf(false)
        private set

    private var activity: Activity? = null
    private var customView: View? = null
    private var callback: WebChromeClient.CustomViewCallback? = null

    fun show(activity: Activity, view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            // A second request while one is showing is a page bug; refuse it
            // rather than stacking views.
            callback.onCustomViewHidden()
            return
        }
        this.activity = activity
        this.customView = view
        this.callback = callback

        view.setBackgroundColor(Color.BLACK)
        decor(activity).addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        isFullscreen = true
    }

    /**
     * Tears the custom view down. Safe to call from the WebView's
     * `onHideCustomView`, from the back button, and on dispose: whichever comes
     * first does the work and the rest are no-ops.
     */
    fun hide() {
        val activity = activity
        val view = customView
        val callback = callback
        this.customView = null
        this.callback = null
        this.activity = null
        isFullscreen = false

        if (activity != null && view != null) {
            decor(activity).removeView(view)
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
        // Tells the page fullscreen ended so it can restore its own layout.
        callback?.onCustomViewHidden()
    }

    private fun decor(activity: Activity): ViewGroup = activity.window.decorView as ViewGroup
}
