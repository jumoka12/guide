package com.ampgames.vidsaver.core.logging

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last uncaught exception, kept on disk.
 *
 * Logcat is read per process, and a crash ends the process, so the one log
 * that matters most is the one the debug-log share can never read. This file
 * is written from the crash handler and included at the top of the next share.
 */
object CrashRecord {

    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_CHARS = 12_000

    fun write(context: Context, thread: Thread, throwable: Throwable) {
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val text = "$stamp on thread ${thread.name}\n$trace".take(MAX_CHARS)
        File(context.filesDir, FILE_NAME).writeText(text)
    }

    /** The recorded crash, or null when there has been none since the last clear. */
    fun read(context: Context): String? =
        File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
