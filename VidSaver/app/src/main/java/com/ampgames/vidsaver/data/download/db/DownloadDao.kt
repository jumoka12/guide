package com.ampgames.vidsaver.data.download.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY createdAt ASC")
    fun observeByStatus(statuses: List<String>): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observeById(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun findById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE url = :url AND status != 'CANCELLED' LIMIT 1")
    suspend fun findByUrl(url: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY createdAt ASC")
    suspend fun listByStatus(statuses: List<String>): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("SELECT fileName FROM downloads")
    suspend fun allFileNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    /**
     * Progress is written on a hot path, so it updates only the three columns
     * that change rather than rewriting the whole row.
     */
    @Query(
        "UPDATE downloads SET bytesDownloaded = :bytesDownloaded, totalBytes = :totalBytes, " +
            "speedBytesPerSecond = :speed WHERE id = :id",
    )
    suspend fun updateProgress(id: Long, bytesDownloaded: Long, totalBytes: Long?, speed: Long)

    @Query("UPDATE downloads SET status = :status, speedBytesPerSecond = 0 WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query(
        "UPDATE downloads SET status = :status, failure = :failure, error = :error, " +
            "retryCount = :retryCount, speedBytesPerSecond = 0 WHERE id = :id",
    )
    suspend fun markFailed(
        id: Long,
        status: String,
        failure: String,
        error: String?,
        retryCount: Int,
    )

    @Query(
        "UPDATE downloads SET status = :status, mediaStoreUri = :uri, completedAt = :completedAt, " +
            "bytesDownloaded = :bytes, totalBytes = :bytes, speedBytesPerSecond = 0, " +
            "failure = 'NONE', error = NULL WHERE id = :id",
    )
    suspend fun markCompleted(id: Long, status: String, uri: String, bytes: Long, completedAt: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED', 'CANCELLED', 'FAILED')")
    suspend fun clearFinished()

    /**
     * Anything left RUNNING belongs to a process that is gone. Called at startup
     * so a killed download shows as paused instead of permanently "downloading".
     */
    @Query("UPDATE downloads SET status = 'PAUSED', speedBytesPerSecond = 0 WHERE status IN ('RUNNING', 'QUEUED')")
    suspend fun resetInterrupted()
}
