package com.ampgames.vidsaver.data.download

import com.ampgames.vidsaver.data.download.db.DownloadDao
import com.ampgames.vidsaver.data.download.db.DownloadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-in for [DownloadDao].
 *
 * Room's own query execution is Google's to test; what matters here is that
 * [DownloadRepository] drives the DAO correctly. A fake keeps those tests on the
 * JVM — no emulator, no Robolectric — so they run on every build.
 *
 * Behaviour mirrors the real DAO's contract: auto-increment ids, insert-ordering,
 * and updates that no-op on a missing row.
 */
class FakeDownloadDao : DownloadDao {

    private val rows = MutableStateFlow<List<DownloadEntity>>(emptyList())
    private var nextId = 1L

    /** Every call made, so tests can assert the hot path stays cheap. */
    val progressUpdates = mutableListOf<Triple<Long, Long, Long>>()

    val snapshot: List<DownloadEntity> get() = rows.value

    override fun observeAll(): Flow<List<DownloadEntity>> =
        rows.map { list -> list.sortedByDescending { it.createdAt } }

    override fun observeByStatus(statuses: List<String>): Flow<List<DownloadEntity>> =
        rows.map { list -> list.filter { it.status in statuses }.sortedBy { it.createdAt } }

    override fun observeById(id: Long): Flow<DownloadEntity?> =
        rows.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun findById(id: Long): DownloadEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun findByUrl(url: String): DownloadEntity? =
        rows.value.firstOrNull { it.url == url && it.status != "CANCELLED" }

    override suspend fun listByStatus(statuses: List<String>): List<DownloadEntity> =
        rows.value.filter { it.status in statuses }.sortedBy { it.createdAt }

    override suspend fun countByStatus(status: String): Int =
        rows.value.count { it.status == status }

    override suspend fun allFileNames(): List<String> = rows.value.map { it.fileName }

    override suspend fun insert(download: DownloadEntity): Long {
        val id = nextId++
        rows.value = rows.value + download.copy(id = id)
        return id
    }

    override suspend fun update(download: DownloadEntity) {
        mutate(download.id) { download }
    }

    override suspend fun updateProgress(
        id: Long,
        bytesDownloaded: Long,
        totalBytes: Long?,
        speed: Long,
    ) {
        progressUpdates += Triple(id, bytesDownloaded, speed)
        mutate(id) {
            it.copy(bytesDownloaded = bytesDownloaded, totalBytes = totalBytes, speedBytesPerSecond = speed)
        }
    }

    override suspend fun updateStatus(id: Long, status: String) {
        mutate(id) { it.copy(status = status, speedBytesPerSecond = 0) }
    }

    override suspend fun markFailed(
        id: Long,
        status: String,
        failure: String,
        error: String?,
        retryCount: Int,
    ) {
        mutate(id) {
            it.copy(
                status = status,
                failure = failure,
                error = error,
                retryCount = retryCount,
                speedBytesPerSecond = 0,
            )
        }
    }

    override suspend fun markCompleted(
        id: Long,
        status: String,
        uri: String,
        bytes: Long,
        completedAt: Long,
    ) {
        mutate(id) {
            it.copy(
                status = status,
                mediaStoreUri = uri,
                bytesDownloaded = bytes,
                totalBytes = bytes,
                completedAt = completedAt,
                speedBytesPerSecond = 0,
                failure = "NONE",
                error = null,
            )
        }
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun clearFinished() {
        rows.value = rows.value.filterNot { it.status in setOf("COMPLETED", "CANCELLED", "FAILED") }
    }

    override suspend fun resetInterrupted() {
        rows.value = rows.value.map {
            if (it.status == "RUNNING" || it.status == "QUEUED") {
                it.copy(status = "PAUSED", speedBytesPerSecond = 0)
            } else {
                it
            }
        }
    }

    private inline fun mutate(id: Long, transform: (DownloadEntity) -> DownloadEntity) {
        rows.value = rows.value.map { if (it.id == id) transform(it) else it }
    }
}
