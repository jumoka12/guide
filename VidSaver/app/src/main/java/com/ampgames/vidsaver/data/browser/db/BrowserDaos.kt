package com.ampgames.vidsaver.data.browser.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    suspend fun exists(url: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM bookmarks")
    suspend fun clear()
}

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<HistoryEntity>>

    @Query(
        "SELECT * FROM history WHERE url LIKE '%' || :query || '%' " +
            "OR title LIKE '%' || :query || '%' ORDER BY visitedAt DESC LIMIT :limit",
    )
    suspend fun search(query: String, limit: Int = 50): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM history")
    suspend fun clear()

    /** Keeps history bounded; called after each insert. */
    @Query(
        "DELETE FROM history WHERE id NOT IN " +
            "(SELECT id FROM history ORDER BY visitedAt DESC LIMIT :keep)",
    )
    suspend fun trimTo(keep: Int)
}

@Dao
interface AllowlistDao {

    @Query("SELECT * FROM adblock_allowlist")
    fun observeAll(): Flow<List<AllowlistEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AllowlistEntry)

    @Query("DELETE FROM adblock_allowlist WHERE host = :host")
    suspend fun delete(host: String)
}
