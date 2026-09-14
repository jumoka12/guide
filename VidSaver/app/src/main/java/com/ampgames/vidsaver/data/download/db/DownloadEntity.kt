package com.ampgames.vidsaver.data.download.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ampgames.vidsaver.domain.download.DownloadFailure
import com.ampgames.vidsaver.domain.download.DownloadStatus
import com.ampgames.vidsaver.domain.media.MediaType

/**
 * One download, from enqueue to file on disk.
 *
 * Enums and the header map are stored as strings rather than through a
 * TypeConverter so the table stays trivially inspectable and a future schema
 * change cannot silently break deserialization of old rows.
 */
@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAt"]),
        Index(value = ["url"]),
    ],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val url: String,
    val pageUrl: String,

    /** Request headers as JSON, so the retry replays exactly what the page sent. */
    @ColumnInfo(defaultValue = "{}") val headersJson: String = "{}",

    val fileName: String,
    val mediaType: String = MediaType.PROGRESSIVE.name,
    val mimeType: String? = null,
    val thumbnailUrl: String? = null,
    val title: String? = null,

    val status: String = DownloadStatus.QUEUED.name,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long? = null,

    /** Bytes per second over the last sampling window; 0 when not running. */
    val speedBytesPerSecond: Long = 0,

    val failure: String = DownloadFailure.NONE.name,
    val error: String? = null,
    val retryCount: Int = 0,

    /** `content://` URI once published to MediaStore; null until then. */
    val mediaStoreUri: String? = null,

    val createdAt: Long,
    val completedAt: Long? = null,
) {
    val statusEnum: DownloadStatus get() = DownloadStatus.fromName(status)
    val failureEnum: DownloadFailure get() = DownloadFailure.fromName(failure)

    val progressFraction: Float?
        get() = totalBytes?.takeIf { it > 0 }
            ?.let { (bytesDownloaded.toFloat() / it).coerceIn(0f, 1f) }
}
