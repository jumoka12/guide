package com.ampgames.vidsaver.ui.browser.web

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * Holds the WebView and measures it with an exact size, always.
 *
 * Android's WebView watches how it is measured. Given anything but an exact
 * height — the way a `wrap_content` view is measured — it assumes its height
 * is content-driven and tells the page engine to lay out with a zero-height
 * viewport, so that percentage heights cannot grow without bound. In that
 * mode `100vh`, `height: 100%` and every full-screen feed layout resolve to
 * zero: the page renders, the video plays, and its box is 384 by 0.
 *
 * Compose measures an embedded view through a wrapper that can pass a
 * "no larger than" height rather than an exact one, which is enough to
 * trigger that mode. This container takes whatever it is given, settles its
 * own size, and then measures the WebView with exactly that size.
 */
class WebViewHost(context: Context) : FrameLayout(context) {

    val webView: WebView = WebView(context).also { view ->
        view.layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        addView(view)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Resolve our own size from whatever the parent offered, as a
        // FrameLayout normally would...
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = measuredWidth
        val height = measuredHeight
        // ...then hand the WebView that size as a fact, not a ceiling.
        webView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        webView.layout(0, 0, right - left, bottom - top)
    }

    /** Detaches the WebView so it can be destroyed without a parent. */
    fun release(): WebView {
        removeView(webView)
        return webView
    }

    companion object {
        /** The host that wraps [view], when it is one of ours. */
        fun of(view: View): WebViewHost? = view as? WebViewHost
    }
}
