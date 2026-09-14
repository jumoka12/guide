package com.ampgames.vidsaver.ui.downloads

import androidx.annotation.StringRes
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.core.format.ByteFormat
import com.ampgames.vidsaver.data.download.db.DownloadEntity
import com.ampgames.vidsaver.domain.download.DownloadFailure
import com.ampgames.vidsaver.domain.download.DownloadStatus

/** One row in the downloads list, with everything the UI needs pre-computed. */
data class DownloadUiItem(
    val id: Long,
    val fileName: String,
    val status: DownloadStatus,
    val failure: DownloadFailure,
    val progressFraction: Float?,
    val sizeLine: String,
    val speedLine: String?,
    val remainingLine: String?,
    val thumbnailUrl: String?,
    val mediaStoreUri: String?,
    @StringRes val errorRes: Int?,
) {
    val canPause: Boolean get() = status == DownloadStatus.RUNNING
    val canResume: Boolean get() = status.isResumable
    val canCancel: Boolean get() = status.isActive || status == DownloadStatus.PAUSED
    val canOpen: Boolean get() = status == DownloadStatus.COMPLETED && mediaStoreUri != null
    val isIndeterminate: Boolean get() = status == DownloadStatus.RUNNING && progressFraction == null
}

data class DownloadsUiState(
    val active: List<DownloadUiItem> = emptyList(),
    val finished: List<DownloadUiItem> = emptyList(),
    val wifiOnly: Boolean = false,
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && active.isEmpty() && finished.isEmpty()
}

/** One-shot message, as a string resource so the ViewModel stays Context-free. */
data class DownloadsMessage(
    @StringRes val resId: Int,
    val args: List<String> = emptyList(),
    val id: Long = System.nanoTime(),
)

fun DownloadEntity.toUiItem(): DownloadUiItem = DownloadUiItem(
    id = id,
    fileName = fileName,
    status = statusEnum,
    failure = failureEnum,
    progressFraction = progressFraction,
    sizeLine = ByteFormat.progress(bytesDownloaded, totalBytes),
    speedLine = if (statusEnum == DownloadStatus.RUNNING) {
        ByteFormat.speed(speedBytesPerSecond)
    } else {
        null
    },
    remainingLine = if (statusEnum == DownloadStatus.RUNNING) {
        ByteFormat.remaining(bytesDownloaded, totalBytes, speedBytesPerSecond)
    } else {
        null
    },
    thumbnailUrl = thumbnailUrl,
    mediaStoreUri = mediaStoreUri,
    errorRes = failureEnum.toMessageRes(),
)

/** Failure reasons the user can act on, phrased for a person rather than a log. */
@StringRes
fun DownloadFailure.toMessageRes(): Int? = when (this) {
    DownloadFailure.NONE -> null
    DownloadFailure.NETWORK -> R.string.download_error_network
    DownloadFailure.HTTP -> R.string.download_error_http
    DownloadFailure.ENCRYPTED -> R.string.download_error_encrypted
    DownloadFailure.LIVE_STREAM -> R.string.download_error_live
    DownloadFailure.STORAGE -> R.string.download_error_storage
    DownloadFailure.UNSUPPORTED -> R.string.download_error_unsupported
    DownloadFailure.UNKNOWN -> R.string.download_error_unknown
}
