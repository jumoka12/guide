package com.ampgames.vidsaver.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Walks the wrapper chain to the hosting Activity, or null when this context is
 * not attached to one (application context, a service, a preview).
 */
fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
