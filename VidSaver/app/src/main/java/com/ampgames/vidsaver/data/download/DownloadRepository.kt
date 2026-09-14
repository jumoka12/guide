package com.ampgames.vidsaver.data.download

import com.ampgames.vidsaver.data.download.db.DownloadDao
import com.ampgames.vidsaver.data.download.db.DownloadEntity
import com.ampgames.vidsaver.domain.download.DownloadFailure
import com.ampgames.vidsaver.domain.download.DownloadStatus
import com.ampgames.vidsaver.domain.media.FileNames
import com.ampgames.vidsaver.domain.media.MediaCandidate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * The record of every download. Owns the `downloads` table and nothing else —
 * the actual transfer lives in [DownloadEngine], which reports back here.
 *
 * Every mutation is idempotent-ish and defensive: a download can be cancelled
 * while a progress update is in flight, so writes never assume the row is still
 * in the state the caller last saw.
 */
@Singleton
class DownloadRepository(
    private val dao: DownloadDao,
    private val clock: () -> Long,
) {

    // Dagger ignores default parameter values, so the injectable constructor is
    // separate from the one tests use to supply a fixed clock.
    @Inject
    constructor(dao: DownloadDao) : this(dao, System::currentTimeMillis)

    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()

    fun observeActive(): Flow<List<DownloadEntity>> = dao.observeByStatus(ACTIVE_STATUSES)

    fun observeFinished(): Flow<List<DownloadEntity>> =
        dao.observeAll().map { all -> all.filter { it.statusEnum.isTerminal } }

    fun observeById(id: Long): Flow<DownloadEntity?> = dao.observeById(id)

    suspend fun find(id: Long): DownloadEntity? = dao.findById(id)

    suspend fun activeCount(): Int = dao.countByStatus(DownloadStatus.RUNNING.name)

    /**
     * Adds [candidate] to the queue.
     *
     * Returns the existing row when the same URL is already queued or finished,
     * so tapping Save twice does not produce two copies. The file name is
     * de-duplicated against names already in the table, so two different videos
     * with the same title do not collide.
     */
    suspend fun enqueue(candidate: MediaCandidate): EnqueueResult {
        dao.findByUrl(candidate.url)?.let { existing ->
            return EnqueueResult(existing.id, alreadyExisted = true)
        }

        val fileName = FileNames.deduplicate(
            candidate.suggestedFileName,
            dao.allFileNames().toSet(),
        )

        val entity = DownloadEntity(
            url = candidate.url,
            pageUrl = candidate.pageUrl,
            headersJson = encodeHeaders(candidate.headers),
            fileName = fileName,
            mediaType = candidate.type.name,
            mimeType = candidate.mimeType,
            thumbnailUrl = candidate.thumbnailUrl,
            title = candidate.title,
            status = DownloadStatus.QUEUED.name,
            totalBytes = candidate.sizeBytes,
            createdAt = clock(),
        )
        val id = dao.insert(entity)
        Timber.d("Enqueued download %d: %s", id, fileName)
        return EnqueueResult(id, alreadyExisted = false)
    }

    suspend fun setStatus(id: Long, status: DownloadStatus) {
        dao.updateStatus(id, status.name)
    }

    suspend fun updateProgress(id: Long, bytesDownloaded: Long, totalBytes: Long?, speed: Long) {
        dao.updateProgress(id, bytesDownloaded, totalBytes, speed)
    }

    suspend fun markCompleted(id: Long, mediaStoreUri: String, sizeBytes: Long) {
        dao.markCompleted(
            id = id,
            status = DownloadStatus.COMPLETED.name,
            uri = mediaStoreUri,
            bytes = sizeBytes,
            completedAt = clock(),
        )
    }

    /**
     * Records a failure. [willRetry] decides whether the row lands in
     * RETRY_SCHEDULED (the worker will pick it up) or FAILED (terminal).
     */
    suspend fun markFailed(
        id: Long,
        failure: DownloadFailure,
        message: String?,
        willRetry: Boolean,
    ) {
        val current = dao.findById(id) ?: return
        dao.markFailed(
            id = id,
            status = if (willRetry) {
                DownloadStatus.RETRY_SCHEDULED.name
            } else {
                DownloadStatus.FAILED.name
            },
            failure = failure.name,
            error = message,
            retryCount = if (willRetry) current.retryCount + 1 else current.retryCount,
        )
    }

    /** Puts a paused or failed download back in the queue and clears its error. */
    suspend fun requeue(id: Long) {
        val current = dao.findById(id) ?: return
        dao.update(
            current.copy(
                status = DownloadStatus.QUEUED.name,
                failure = DownloadFailure.NONE.name,
                error = null,
                speedBytesPerSecond = 0,
            ),
        )
    }

    suspend fun cancel(id: Long) {
        dao.updateStatus(id, DownloadStatus.CANCELLED.name)
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clearFinished() = dao.clearFinished()

    /** Rows this many retries deep have exhausted their budget. */
    suspend fun hasRetriesLeft(id: Long): Boolean =
        (dao.findById(id)?.retryCount ?: 0) < MAX_RETRIES

    suspend fun nextQueued(limit: Int): List<DownloadEntity> =
        dao.listByStatus(listOf(DownloadStatus.QUEUED.name)).take(limit)

    suspend fun pendingRetries(): List<DownloadEntity> =
        dao.listByStatus(listOf(DownloadStatus.RETRY_SCHEDULED.name))

    /** Everything the user could resume: paused, failed or awaiting a retry. */
    suspend fun resumable(): List<DownloadEntity> = dao.listByStatus(
        listOf(
            DownloadStatus.PAUSED.name,
            DownloadStatus.RETRY_SCHEDULED.name,
            DownloadStatus.QUEUED.name,
        ),
    )

    /**
     * Called once at startup: any row still marked RUNNING or QUEUED belongs to
     * a process that no longer exists, so it is shown as paused rather than
     * appearing to download forever.
     */
    suspend fun resetInterrupted() = dao.resetInterrupted()

    fun decodeHeaders(entity: DownloadEntity): Map<String, String> =
        runCatching { json.decodeFromString(headerSerializer, entity.headersJson) }
            .getOrElse {
                Timber.w(it, "Could not decode headers for download %d", entity.id)
                emptyMap()
            }

    private fun encodeHeaders(headers: Map<String, String>): String =
        runCatching { json.encodeToString(headerSerializer, headers) }.getOrDefault("{}")

    data class EnqueueResult(val id: Long, val alreadyExisted: Boolean)

    companion object {
        const val MAX_RETRIES = 3

        val ACTIVE_STATUSES: List<String> = listOf(
            DownloadStatus.QUEUED.name,
            DownloadStatus.RUNNING.name,
            DownloadStatus.PAUSED.name,
            DownloadStatus.RETRY_SCHEDULED.name,
        )

        private val json = Json { ignoreUnknownKeys = true }
        private val headerSerializer = MapSerializer(String.serializer(), String.serializer())
    }
}
