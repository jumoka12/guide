package com.ampgames.vidsaver.ui.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * The runtime permissions a download needs, requested **in context** — at the
 * moment the user asks to save a video, never on a cold start. Google Play
 * expects the request to be tied to the feature that needs it.
 *
 * Neither permission is a hard requirement:
 * - `POST_NOTIFICATIONS` (Android 13+) only affects whether progress is visible
 *   in the shade. Denied, the download still runs.
 * - `WRITE_EXTERNAL_STORAGE` is needed only on API 28 and below, where there is
 *   no scoped storage. On API 29+ it is never requested.
 *
 * So [request] is fire-and-forget: the caller starts the download regardless of
 * the answer.
 */
class DownloadPermissions internal constructor(
    val allGranted: Boolean,
    private val missing: List<String>,
    private val launch: (Array<String>) -> Unit,
) {
    /** Asks for anything still missing. Does nothing when there is nothing to ask. */
    fun request() {
        if (missing.isEmpty()) return
        launch(missing.toTypedArray())
    }
}

@Composable
fun rememberDownloadPermissions(): DownloadPermissions {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(requiredPermissions().filter { context.hasPermission(it) }) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        granted = granted + results.filterValues { it }.keys
    }

    val required = requiredPermissions()
    val missing = required.filterNot { it in granted || context.hasPermission(it) }

    return DownloadPermissions(
        allGranted = missing.isEmpty(),
        missing = missing,
        launch = { launcher.launch(it) },
    )
}

private fun requiredPermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
    // Scoped storage from API 29 on means no storage permission at all.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}

private fun android.content.Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
