package com.ampgames.vidsaver.data.browser.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks", indices = [Index(value = ["url"], unique = true)])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val createdAt: Long,
)

@Entity(tableName = "history", indices = [Index(value = ["visitedAt"]), Index(value = ["url"])])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long,
)

/** A page host the user has turned ad blocking off for. */
@Entity(tableName = "adblock_allowlist")
data class AllowlistEntry(
    @PrimaryKey val host: String,
)
