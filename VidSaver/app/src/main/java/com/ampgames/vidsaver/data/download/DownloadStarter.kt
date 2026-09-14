package com.ampgames.vidsaver.data.download

import android.content.Context
import com.ampgames.vidsaver.domain.media.MediaCandidate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one way a download gets created: record it, then wake the service.
 *
 * Exists so callers (the browser today, the gallery and the paywall later) do
 * not each need the repository, the service and the ordering between them.
 */
@Singleton
class DownloadStarter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository,
) {

    suspend fun enqueue(candidate: MediaCandidate): DownloadRepository.EnqueueResult {
        val result = repository.enqueue(candidate)
        // Even for a duplicate: the existing row may be paused or failed, and
        // the user just asked for it again.
        if (result.alreadyExisted) repository.requeue(result.id)
        DownloadService.start(context)
        return result
    }
}
