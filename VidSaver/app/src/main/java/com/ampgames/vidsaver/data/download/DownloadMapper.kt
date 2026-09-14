package com.ampgames.vidsaver.data.download

import com.ampgames.vidsaver.data.download.db.DownloadEntity
import com.ampgames.vidsaver.domain.media.MediaCandidate
import com.ampgames.vidsaver.domain.media.MediaType

/**
 * Rebuilds the [MediaCandidate] a download was created from, so a retry days
 * later replays exactly the request the page originally made.
 */
fun DownloadEntity.toCandidate(headers: Map<String, String>): MediaCandidate = MediaCandidate(
    url = url,
    pageUrl = pageUrl,
    type = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.PROGRESSIVE),
    headers = headers,
    mimeType = mimeType,
    sizeBytes = totalBytes,
    thumbnailUrl = thumbnailUrl,
    title = title,
    suggestedFileName = fileName,
)
